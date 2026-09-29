package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 会话对象 biz_ai_chat_session（二期）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_ai_chat_session")
public class BizAiChatSession implements Serializable
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话标识（对外展示/引用用，唯一） */
    private String sessionNo;

    /** 所属用户ID */
    private Long memberId;

    /** 会话标题（取首次提问前 20 字） */
    private String title;

    /** 消息条数 */
    private Integer messageCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastActiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public Date getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(Date lastActiveTime) { this.lastActiveTime = lastActiveTime; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
