package com.ruoyi.merchant.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizShopInfo;
import com.ruoyi.merchant.mapper.BizShopInfoMapper;
import com.ruoyi.merchant.service.ShopInfoService;

/**
 * 店铺信息 服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ShopInfoServiceImpl implements ShopInfoService
{
    /** 单店固定 shop_id = 1 */
    private static final Long SHOP_ID = 1L;

    @Autowired
    private BizShopInfoMapper shopInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public BizShopInfo getShopInfo()
    {
        BizShopInfo shopInfo = shopInfoMapper.selectById(SHOP_ID);
        if (shopInfo == null)
        {
            throw new ServiceException("店铺信息不存在，请先初始化店铺信息");
        }
        return shopInfo;
    }

    @Override
    public int updateShopInfo(BizShopInfo shopInfo)
    {
        // shop_id 固定 1，忽略前端传入的店铺ID
        shopInfo.setShopId(SHOP_ID);
        int rows = shopInfoMapper.updateById(shopInfo);
        if (rows > 0)
        {
            // Cache-Aside：先更新数据库，再删除缓存（只删不更新，由下次读请求回填）
            redisCache.deleteObject(TakeoutCacheKeys.SHOP_INFO);
        }
        return rows;
    }
}
