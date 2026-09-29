package com.ruoyi.userapi.service;

import com.ruoyi.userapi.domain.vo.ShopInfoVo;

/**
 * 小程序端店铺信息服务接口
 *
 * @author 阿婆干饭社
 */
public interface ApiShopService
{
    /**
     * 查询店铺信息（Cache-Aside：读 takeout:shop:1，miss 查库回填；
     * 缓存失效由管理端写操作删除，另设 24h TTL 兜底防漏删）
     */
    ShopInfoVo getShopInfo();
}
