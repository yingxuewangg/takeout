package com.ruoyi.userapi.service.impl;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizShopInfo;
import com.ruoyi.merchant.mapper.BizShopInfoMapper;
import com.ruoyi.userapi.domain.vo.ShopInfoVo;
import com.ruoyi.userapi.service.ApiShopService;

/**
 * 小程序端店铺信息服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiShopServiceImpl implements ApiShopService
{
    private static final Long SHOP_ID = 1L;

    /** 缓存 TTL 兜底（正常由管理端写操作删除缓存，TTL 防极端漏删） */
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired
    private BizShopInfoMapper shopInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public ShopInfoVo getShopInfo()
    {
        ShopInfoVo cached = null;
        try
        {
            cached = redisCache.getCacheObject(TakeoutCacheKeys.SHOP_INFO);
        }
        catch (Exception e)
        {
            // 缓存损坏容错：视为 miss 删除后回填，避免接口 500
            redisCache.deleteObject(TakeoutCacheKeys.SHOP_INFO);
        }
        if (cached != null)
        {
            return cached;
        }
        BizShopInfo shopInfo = shopInfoMapper.selectById(SHOP_ID);
        ShopInfoVo vo = new ShopInfoVo();
        if (shopInfo != null)
        {
            vo.setShopId(shopInfo.getShopId());
            vo.setShopName(shopInfo.getShopName());
            vo.setAddress(shopInfo.getAddress());
            vo.setBusinessHours(shopInfo.getBusinessHours());
            vo.setBusinessStatus(shopInfo.getBusinessStatus());
            vo.setPhone(shopInfo.getPhone());
            vo.setNotice(shopInfo.getNotice());
            vo.setDeliveryFee(shopInfo.getDeliveryFee());
            vo.setMinDeliveryAmount(shopInfo.getMinDeliveryAmount());
        }
        // Cache-Aside：miss 回填（只删不更新约定的读侧）
        redisCache.setCacheObject(TakeoutCacheKeys.SHOP_INFO, vo, (int) CACHE_TTL_HOURS, TimeUnit.HOURS);
        return vo;
    }
}
