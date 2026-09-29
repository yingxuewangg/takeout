package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 知识库文档对象 biz_ai_knowledge（二期）。
 * 向量数据存 Redis（RedisVectorStore/RediSearch），本表只存文档元数据与解析状态。
 * 文件字段遵循一期约定：只存相对路径，不存域名。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_ai_knowledge")
public class BizAiKnowledge implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 解析状态：待解析 */
    public static final int STATUS_PENDING = 0;
    /** 解析状态：解析中 */
    public static final int STATUS_PARSING = 1;
    /** 解析状态：解析成功 */
    public static final int STATUS_SUCCESS = 2;
    /** 解析状态：解析失败 */
    public static final int STATUS_FAILED = 3;

    /** 文档ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件相对路径（只存相对路径，不存域名） */
    private String filePath;

    /** 文件类型（txt/md/pdf/docx） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 切片数量（解析成功后写入） */
    private Integer chunkCount;

    /** 解析状态（0待解析 1解析中 2解析成功 3解析失败） */
    private Integer parseStatus;

    /** 失败原因（解析失败时写入） */
    private String failReason;

    /** 向量索引标识（RedisVectorStore 索引名，便于按文档清理向量） */
    private String vectorIndexName;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Integer getParseStatus() { return parseStatus; }
    public void setParseStatus(Integer parseStatus) { this.parseStatus = parseStatus; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getVectorIndexName() { return vectorIndexName; }
    public void setVectorIndexName(String vectorIndexName) { this.vectorIndexName = vectorIndexName; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
