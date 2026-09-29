package com.ruoyi.merchant.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;

/**
 * AI 基础 Bean 配置（二期 RAG）。
 *
 * 说明：
 * - ChatModel / EmbeddingModel 由 Spring AI 自动装配提供（OpenAI 兼容协议）；
 * - **VectorStore 手工装配**：Spring AI 的 RedisVectorStore 自动配置强依赖
 *   JedisConnectionFactory，而若依用 Lettuce，二者冲突（启动即失败）。
 *   因此本项目自行创建 Jedis 客户端并构建 RedisVectorStore（见下方注释），
 *   已在 application.yml 中排除该自动配置（spring.autoconfigure.exclude）。
 * - Chat 与 Embedding 通过不同配置前缀分离，可按需指向不同供应商（如 Chat 用 DeepSeek）。
 *
 * @author 阿婆干饭社
 */
@Configuration
public class AiBeanConfig
{
    /** 切片目标长度（字符数，方案要求"按段落+长度切片"） */
    @Value("${takeout.ai.chunk.size:800}")
    private int chunkSize;

    /** 切片最小长度（不足则与相邻块合并，避免碎片） */
    @Value("${takeout.ai.chunk.min-length:200}")
    private int chunkMinLength;

    /** 切片最小嵌入长度（小于此长度的块不参与向量化） */
    @Value("${takeout.ai.chunk.min-embed-length:50}")
    private int chunkMinEmbedLength;

    /** Redis 连接信息（与若依缓存同一实例，仅客户端实现不同：向量库用 Jedis） */
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /** 向量索引名 */
    @Value("${spring.ai.vectorstore.redis.index-name:takeout-ai-index}")
    private String indexName;

    /** 向量 key 前缀 */
    @Value("${spring.ai.vectorstore.redis.prefix:takeout:ai:vector:}")
    private String vectorPrefix;

    /** 是否自动创建索引 */
    @Value("${spring.ai.vectorstore.redis.initialize-schema:true}")
    private boolean initializeSchema;

    @Value("${spring.ai.vectorstore.redis.hnsw.m:16}")
    private int hnswM;

    @Value("${spring.ai.vectorstore.redis.hnsw.ef-construction:200}")
    private int hnswEfConstruction;

    @Value("${spring.ai.vectorstore.redis.hnsw.ef-runtime:10}")
    private int hnswEfRuntime;

    /**
     * 文档切片器：按标点/段落边界切分，控制单块长度并保留分隔符，
     * 使检索片段语义相对完整（方案要求"按段落+长度切片"）。
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter()
    {
        return TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(chunkMinLength)
                .withMinChunkLengthToEmbed(chunkMinEmbedLength)
                .withMaxNumChunks(10000)
                // 保留换行作为切分边界（段落语义）
                .withKeepSeparator(true)
                .build();
    }

    /**
     * Jedis 客户端 Bean（与若依 Redis 同一实例；向量库与向量兜底清理共用）。
     * 若依缓存使用 Lettuce，此处单独创建 Jedis 仅服务于向量功能，互不影响。
     */
    @Bean(destroyMethod = "close")
    public RedisClient vectorJedisClient()
    {
        return buildJedisClient();
    }

    /**
     * Redis 向量库（RedisVectorStore，需 redis-stack 镜像提供 RediSearch 模块）。
     * 手工装配原因见类注释：避免与若依 Lettuce 配置冲突。
     * 向量维度由 EmbeddingModel 自动探测，无需配置（换模型需重建索引）。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel, RedisClient vectorJedisClient)
    {
        return RedisVectorStore.builder(vectorJedisClient, embeddingModel)
                .indexName(indexName)
                .prefix(vectorPrefix)
                .initializeSchema(initializeSchema)
                .hnswM(hnswM)
                .hnswEfConstruction(hnswEfConstruction)
                .hnswEfRuntime(hnswEfRuntime)
                .build();
    }

    /** 构建 Jedis 客户端（连接参数与若依 Redis 配置保持一致；向量库需 Jedis，若依缓存用 Lettuce） */
    private RedisClient buildJedisClient()
    {
        if (redisPassword != null && !redisPassword.isEmpty())
        {
            // 有密码：user 传 null 表示仅用密码认证（Redis 6+ ACL 的默认用户）
            return RedisClient.create(redisHost, redisPort, null, redisPassword);
        }
        return RedisClient.create(redisHost, redisPort);
    }

    /**
     * 通用 ChatClient（用户端问答使用）。
     * system 提示词与历史消息在调用处动态组装（含在售菜品与检索片段）。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel)
    {
        return ChatClient.builder(chatModel).build();
    }
}
