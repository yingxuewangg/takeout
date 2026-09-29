package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 小程序用户对象 biz_member
 *
 * @author 阿婆干饭社
 */
@TableName("biz_member")
public class BizMember implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 微信openid（小程序端唯一标识） */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像（只存相对路径） */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 最近登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    private Date createTime;

    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Date getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
