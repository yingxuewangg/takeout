package com.ruoyi.merchant.service;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.merchant.domain.BizAiKnowledge;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 知识库服务接口（二期 RAG，管理端）。
 * 流程：上传文档 → 落库（待解析）→ 异步解析（Tika 读取 → 切片 → Embedding → 写入 VectorStore）
 *
 * @author 阿婆干饭社
 */
public interface AiKnowledgeService
{
    /**
     * 上传知识库文档（txt/md/pdf/docx）：保存文件 + 落库（待解析）+ 触发异步解析
     */
    BizAiKnowledge upload(MultipartFile file, String operator);

    /**
     * 知识库文档分页列表（含解析状态与切片数）
     */
    IPage<BizAiKnowledge> listKnowledge(String fileName, Integer parseStatus, long pageNum, long pageSize);

    /**
     * 删除文档：同步删除其全部向量 + 删除物理文件 + 删除记录
     */
    void remove(Long id);

    /**
     * 重新解析：先删除旧向量，再重新解析入库（状态回到待解析→解析中→成功/失败）
     */
    void reparse(Long id);

    /**
     * 解析文档（异步任务体）：Tika 读取 → 切片 → 向量化 → 写入 VectorStore；
     * 失败时记录 parse_status=3 与失败原因
     */
    void parseDocument(Long id);
}
