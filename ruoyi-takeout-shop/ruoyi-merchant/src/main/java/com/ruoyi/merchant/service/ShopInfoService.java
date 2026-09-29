package com.ruoyi.merchant.service;

import com.ruoyi.merchant.domain.BizShopInfo;

/**
 * 店铺信息 服务层
 *
 * @author 阿婆干饭社
 */
public interface ShopInfoService
{
    /**
     * 查询店铺信息（shop_id 固定 1）
     */
    BizShopInfo getShopInfo();

    /**
     * 修改店铺信息（含配送费/起送价/营业状态）
     * 先更新数据库，再删除缓存 takeout:shop:1（只删不更新）
     *
     * @return 影响行数
     */
    int updateShopInfo(BizShopInfo shopInfo);
}
