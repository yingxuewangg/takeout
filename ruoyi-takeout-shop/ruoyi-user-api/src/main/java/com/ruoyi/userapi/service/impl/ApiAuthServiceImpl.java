package com.ruoyi.userapi.service.impl;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.userapi.domain.BizMember;
import com.ruoyi.userapi.domain.vo.LoginVo;
import com.ruoyi.userapi.mapper.BizMemberMapper;
import com.ruoyi.userapi.service.ApiAuthService;
import com.ruoyi.userapi.service.WxApiClient;

/**
 * 小程序认证服务实现。
 * 登录流程：code -> openid（真实 code2session / 开发辅助 mock）-> 查或建 biz_member
 *          -> 生成 token -> Redis takeout:token:{token}=memberId（7 天）-> 返回 token。
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiAuthServiceImpl implements ApiAuthService
{
    /** 小程序登录态有效期（天） */
    private static final int TOKEN_EXPIRE_DAYS = 7;

    @Autowired
    private BizMemberMapper memberMapper;

    @Autowired
    private WxApiClient wxApiClient;

    @Autowired
    private RedisCache redisCache;

    /**
     * 开发辅助开关：true 时 code 直接作为 openid（跳过微信接口），仅限本地开发调试，
     * 部署上线必须为 false 并配置真实 appid/secret（与 payment.mock.enabled 同风格）
     */
    @Value("${takeout.wx.mock-enabled:false}")
    private boolean mockEnabled;

    @Override
    public LoginVo login(String code)
    {
        if (!StringUtils.hasText(code))
        {
            throw new ServiceException("code 不能为空");
        }
        String openid = mockEnabled ? code : wxApiClient.code2Session(code);

        // 查或建用户（openid 唯一索引兜底并发）
        BizMember member = memberMapper.selectOne(new LambdaQueryWrapper<BizMember>().eq(BizMember::getOpenid, openid));
        boolean newUser = false;
        if (member == null)
        {
            newUser = true;
            member = new BizMember();
            member.setOpenid(openid);
            member.setNickname("干饭人" + IdUtils.randomUUID().substring(0, 4));
            member.setCreateTime(DateUtils.getNowDate());
            try
            {
                memberMapper.insert(member);
            }
            catch (DuplicateKeyException e)
            {
                // 并发首登：另一请求已插入，改查
                member = memberMapper.selectOne(new LambdaQueryWrapper<BizMember>().eq(BizMember::getOpenid, openid));
                newUser = false;
            }
        }
        member.setLastLoginTime(DateUtils.getNowDate());
        memberMapper.updateById(member);

        // 签发 token
        String token = IdUtils.fastSimpleUUID();
        redisCache.setCacheObject(TakeoutCacheKeys.MEMBER_TOKEN_KEY + token, member.getId(), TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);

        LoginVo vo = new LoginVo();
        vo.setToken(token);
        vo.setMemberId(member.getId());
        vo.setNickname(member.getNickname());
        vo.setAvatar(member.getAvatar());
        vo.setPhone(member.getPhone());
        vo.setNewUser(newUser);
        return vo;
    }
}
