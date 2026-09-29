package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 会话消息对象 biz_ai_chat_message（二期）。
 * assistant 消息的 referencesJson 记录命中的知识片段（便于验证 RAG 生效与排障）。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_ai_chat_message")
public class BizAiChatMessage implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** 角色（user/assistant） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 命中知识片段引用（JSON数组，仅 assistant 消息有） */
    private String referencesJson;

    /** 本次回答耗时（毫秒） */
    private Long costMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getReferencesJson() { return referencesJson; }
    public void setReferencesJson(String referencesJson) { this.referencesJson = referencesJson; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
