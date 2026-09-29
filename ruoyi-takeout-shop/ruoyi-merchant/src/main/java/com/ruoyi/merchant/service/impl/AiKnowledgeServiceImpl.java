package com.ruoyi.merchant.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.merchant.domain.BizAiKnowledge;
import com.ruoyi.merchant.mapper.BizAiKnowledgeMapper;
import com.ruoyi.merchant.service.AiKnowledgeService;

/**
 * AI 知识库服务实现（二期 RAG）。
 *
 * 解析链路：Tika 读取文档 → TokenTextSplitter 按段落+长度切片 →
 * 每片写入 metadata(knowledgeId/fileName/chunkIndex) → VectorStore 批量向量化入库（Redis）。
 * 删除文档时按 metadata 过滤删除其全部向量，保证知识库与向量一致。
 *
 * @author 阿婆干饭社
 */
@Service
public class AiKnowledgeServiceImpl implements AiKnowledgeService
{
    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeServiceImpl.class);

    /** 允许的文档类型（方案：txt/md/pdf/docx） */
    private static final String[] ALLOWED_EXTENSIONS = { "txt", "md", "pdf", "docx" };

    /** metadata 中标识来源文档的键（删除/检索时使用） */
    public static final String META_KNOWLEDGE_ID = "knowledgeId";

    /** metadata 中记录来源文件名的键（回答中展示来源） */
    public static final String META_FILE_NAME = "fileName";

    @Autowired
    private BizAiKnowledgeMapper knowledgeMapper;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    /** 与 AiBeanConfig 中同一配置；兜底清理向量时直连使用 */
    @Value("${spring.ai.vectorstore.redis.index-name:takeout-ai-index}")
    private String vectorIndexName;

    @Value("${spring.ai.vectorstore.redis.prefix:takeout:ai:vector:}")
    private String vectorPrefix;

    /** 兜底清理向量用：若依已有的 Redis 连接（Lettuce），执行原始 FT.SEARCH / DEL 命令 */
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 解析任务执行线程池（与定时任务线程分开，避免相互阻塞） */
    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private Executor parseExecutor;

    @Value("${takeout.ai.chunk.max-embed-batch:20}")
    private int maxEmbedBatch;

    @Override
    public BizAiKnowledge upload(MultipartFile file, String operator)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);
        if (!isAllowed(ext))
        {
            throw new ServiceException("仅支持 txt/md/pdf/docx 格式的知识库文档");
        }
        String relativePath;
        try
        {
            // 复用一期统一上传规则（日期目录 + UUID 重命名），返回相对路径
            relativePath = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, ALLOWED_EXTENSIONS, true);
        }
        catch (Exception e)
        {
            throw new ServiceException("文档上传失败：" + e.getMessage());
        }

        Date now = DateUtils.getNowDate();
        BizAiKnowledge knowledge = new BizAiKnowledge();
        knowledge.setFileName(originalName);
        knowledge.setFilePath(relativePath);
        knowledge.setFileType(ext);
        knowledge.setFileSize(file.getSize());
        knowledge.setChunkCount(0);
        knowledge.setParseStatus(BizAiKnowledge.STATUS_PENDING);
        knowledge.setFailReason("");
        knowledge.setVectorIndexName("");
        knowledge.setCreateBy(operator);
        knowledge.setCreateTime(now);
        knowledge.setUpdateTime(now);
        knowledgeMapper.insert(knowledge);

        // 异步解析（上传接口立即返回，列表可看到状态流转）
        final Long id = knowledge.getId();
        parseExecutor.execute(() -> {
            try
            {
                parseDocument(id);
            }
            catch (Exception e)
            {
                log.error("[AI知识库] 异步解析异常：id={}", id, e);
            }
        });
        return knowledge;
    }

    @Override
    public IPage<BizAiKnowledge> listKnowledge(String fileName, Integer parseStatus, long pageNum, long pageSize)
    {
        LambdaQueryWrapper<BizAiKnowledge> wrapper = new LambdaQueryWrapper<BizAiKnowledge>()
                .like(StringUtils.hasText(fileName), BizAiKnowledge::getFileName, fileName)
                .eq(parseStatus != null, BizAiKnowledge::getParseStatus, parseStatus)
                .orderByDesc(BizAiKnowledge::getId);
        return knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void remove(Long id)
    {
        BizAiKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null)
        {
            throw new ServiceException("文档不存在");
        }
        // 1. 同步删除该文档的全部向量（按 metadata 过滤）
        deleteVectors(id);
        // 2. 删除物理文件
        deletePhysicalFile(knowledge.getFilePath());
        // 3. 删除记录
        knowledgeMapper.deleteById(id);
        log.info("[AI知识库] 文档已删除（含向量）：id={} file={}", id, knowledge.getFileName());
    }

    @Override
    public void reparse(Long id)
    {
        BizAiKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null)
        {
            throw new ServiceException("文档不存在");
        }
        // 先清旧向量，避免重复内容累积
        deleteVectors(id);
        BizAiKnowledge update = new BizAiKnowledge();
        update.setId(id);
        update.setParseStatus(BizAiKnowledge.STATUS_PENDING);
        update.setChunkCount(0);
        update.setFailReason("");
        update.setUpdateTime(DateUtils.getNowDate());
        knowledgeMapper.updateById(update);
        parseExecutor.execute(() -> {
            try
            {
                parseDocument(id);
            }
            catch (Exception e)
            {
                log.error("[AI知识库] 重新解析异常：id={}", id, e);
            }
        });
        log.info("[AI知识库] 已触发重新解析：id={}", id);
    }

    @Override
    public void parseDocument(Long id)
    {
        BizAiKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null)
        {
            return;
        }
        // 状态 -> 解析中
        markStatus(id, BizAiKnowledge.STATUS_PARSING, null, null);
        try
        {
            File file = new File(resolveAbsolutePath(knowledge.getFilePath()));
            if (!file.exists())
            {
                throw new ServiceException("源文件不存在，请重新上传：" + knowledge.getFilePath());
            }
            // 1. Tika 读取（自动识别 txt/md/pdf/docx）
            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(file));
            List<Document> rawDocs = reader.get();
            if (rawDocs == null || rawDocs.isEmpty())
            {
                throw new ServiceException("文档内容为空或无法解析出文本");
            }
            // 2. 按段落+长度切片
            List<Document> chunks = tokenTextSplitter.apply(rawDocs);
            if (chunks == null || chunks.isEmpty())
            {
                throw new ServiceException("文档切片结果为空，请检查文档内容");
            }
            // 3. 为每个切片附加来源 metadata（供检索溯源与按文档删除）
            List<Document> toStore = new ArrayList<>(chunks.size());
            int index = 0;
            for (Document chunk : chunks) {
                String text = chunk.getText();
                if (text == null || text.trim().isEmpty())
                {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                // 用 Long 类型存文档ID：与删除时过滤表达式的类型保持一致（避免 RediSearch 类型不匹配导致删除失败）
                metadata.put(META_KNOWLEDGE_ID, id);
                metadata.put(META_FILE_NAME, knowledge.getFileName());
                metadata.put("chunkIndex", index++);
                // 重建 Document 以携带 metadata（原 Document 的 metadata 为只读视图）
                toStore.add(new Document(text.trim(), metadata));
            }
            if (toStore.isEmpty())
            {
                throw new ServiceException("文档切片后无有效文本内容");
            }
            // 4. 分批向量化写入 RedisVectorStore（Embedding 模型由此自动调用）
            for (int i = 0; i < toStore.size(); i += maxEmbedBatch)
            {
                List<Document> batch = toStore.subList(i, Math.min(i + maxEmbedBatch, toStore.size()));
                vectorStore.add(batch);
                log.info("[AI知识库] 向量化进度：id={} {}/{}", id, Math.min(i + maxEmbedBatch, toStore.size()), toStore.size());
            }
            // 5. 状态 -> 成功，记录切片数
            markStatus(id, BizAiKnowledge.STATUS_SUCCESS, toStore.size(), null);
            log.info("[AI知识库] 解析成功：id={} file={} 切片数={}", id, knowledge.getFileName(), toStore.size());
        }
        catch (Exception e)
        {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (reason.length() > 480)
            {
                reason = reason.substring(0, 480) + "...";
            }
            markStatus(id, BizAiKnowledge.STATUS_FAILED, null, reason);
            log.error("[AI知识库] 解析失败：id={} 原因={}", id, reason);
        }
    }

    /**
     * 按文档删除其全部向量。
     *
     * 机制说明（实测确认）：RedisVectorStore 的索引 schema 只包含 content 与 embedding，
     * 写入的 metadata **不参与索引**，因此既不能用 Filter 表达式删除（SDK 返回 Failed to delete
     * documents by filter），也不能用 FT.SEARCH 按 metadata 检索。故采用最可靠的方式：
     * 按向量 key 前缀扫描，读取 JSON 内容中的 knowledgeId 匹配后删除，删除后按需重建索引。
     */
    private void deleteVectors(Long knowledgeId)
    {
        try
        {
            java.util.Set<String> keys = stringRedisTemplate.keys(vectorPrefix + "*");
            if (keys == null || keys.isEmpty())
            {
                log.info("[AI知识库] 无向量数据需清理：knowledgeId={}", knowledgeId);
                return;
            }
            List<String> toDelete = new ArrayList<>();
            for (String key : keys)
            {
                // RedisVectorStore 用 ReJSON 存储（JSON.SET），必须用 JSON.GET 读取；
                // 对 JSON 类型调用 opsForValue().get() 会抛 WRONGTYPE，故不用它。
                String json = readJsonValue(key);
                if (json == null)
                {
                    continue;
                }
                if (json.contains("\"" + META_KNOWLEDGE_ID + "\":\"" + knowledgeId + "\"")
                        || json.contains("\"" + META_KNOWLEDGE_ID + "\":" + knowledgeId))
                {
                    toDelete.add(key);
                }
            }
            if (!toDelete.isEmpty())
            {
                stringRedisTemplate.delete(toDelete);
                log.info("[AI知识库] 已删除文档向量：knowledgeId={} 删除 {} 条", knowledgeId, toDelete.size());
            }
            else
            {
                log.info("[AI知识库] 未找到该文档的向量（可能已清理）：knowledgeId={}", knowledgeId);
            }
        }
        catch (Exception e)
        {
            log.error("[AI知识库] 删除向量失败（请手工检查 Redis 向量数据）：knowledgeId={}", knowledgeId, e);
        }
    }

    /** 读取 ReJSON 类型的值（RedisVectorStore 用 JSON.SET 写入） */
    private String readJsonValue(String key)
    {
        try
        {
            Object result = stringRedisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                            connection.execute("JSON.GET",
                                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if (result instanceof byte[])
            {
                return new String((byte[]) result, java.nio.charset.StandardCharsets.UTF_8);
            }
            return result == null ? null : String.valueOf(result);
        }
        catch (Exception e)
        {
            log.debug("[AI知识库] 读取向量 JSON 失败：key={} 原因={}", key, e.getMessage());
            return null;
        }
    }

    /** 更新解析状态（chunkCount/failReason 传 null 表示不修改） */
    private void markStatus(Long id, int status, Integer chunkCount, String failReason)
    {
        BizAiKnowledge update = new BizAiKnowledge();
        update.setId(id);
        update.setParseStatus(status);
        if (chunkCount != null)
        {
            update.setChunkCount(chunkCount);
        }
        if (failReason != null)
        {
            update.setFailReason(failReason);
        }
        update.setUpdateTime(DateUtils.getNowDate());
        knowledgeMapper.updateById(update);
    }

    /** 相对路径（/profile/upload/yyyy/MM/dd/xx.ext）-> 绝对路径 */
    private String resolveAbsolutePath(String relativePath)
    {
        String path = relativePath;
        String prefix = com.ruoyi.common.constant.Constants.RESOURCE_PREFIX; // /profile
        if (path.startsWith(prefix))
        {
            path = path.substring(prefix.length());
        }
        return RuoYiConfig.getProfile() + path;
    }

    private void deletePhysicalFile(String relativePath)
    {
        try
        {
            File file = new File(resolveAbsolutePath(relativePath));
            if (file.exists() && !file.delete())
            {
                log.warn("[AI知识库] 物理文件删除失败：{}", relativePath);
            }
        }
        catch (Exception e)
        {
            log.warn("[AI知识库] 物理文件删除异常：{} 原因={}", relativePath, e.getMessage());
        }
    }

    private String getExtension(String fileName)
    {
        if (fileName == null || fileName.lastIndexOf('.') < 0)
        {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isAllowed(String ext)
    {
        for (String allowed : ALLOWED_EXTENSIONS)
        {
            if (allowed.equals(ext))
            {
                return true;
            }
        }
        return false;
    }
}
