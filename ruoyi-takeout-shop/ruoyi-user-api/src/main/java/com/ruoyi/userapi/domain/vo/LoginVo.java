package com.ruoyi.userapi.domain.vo;

/**
 * 小程序登录结果 VO
 *
 * @author 阿婆干饭社
 */
public class LoginVo
{
    /** 登录态 token（Redis takeout:token:{token}，7 天有效） */
    private String token;

    /** 用户ID */
    private Long memberId;

    /** 昵称 */
    private String nickname;

    /** 头像完整 URL（后端拼接） */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 是否新用户（首次登录） */
    private Boolean newUser;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Boolean getNewUser() { return newUser; }
    public void setNewUser(Boolean newUser) { this.newUser = newUser; }
}
