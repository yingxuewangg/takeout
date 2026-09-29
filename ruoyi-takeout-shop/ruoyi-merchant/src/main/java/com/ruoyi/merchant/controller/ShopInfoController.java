package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.merchant.domain.BizShopInfo;
import com.ruoyi.merchant.service.ShopInfoService;

/**
 * 店铺信息管理（单店，shop_id 固定 1）
 * 包含配送费、起送价、营业状态（打烊后用户可浏览不可下单）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/shop")
public class ShopInfoController extends BaseController
{
    @Autowired
    private ShopInfoService shopInfoService;

    /**
     * 查询店铺信息
     */
    @PreAuthorize("@ss.hasPermi('merchant:shop:query')")
    @GetMapping
    public AjaxResult getInfo()
    {
        return success(shopInfoService.getShopInfo());
    }

    /**
     * 修改店铺信息（修改后删除 takeout:shop:1 缓存）
     */
    @PreAuthorize("@ss.hasPermi('merchant:shop:edit')")
    @Log(title = "店铺信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizShopInfo shopInfo)
    {
        shopInfo.setUpdateBy(getUsername());
        return toAjax(shopInfoService.updateShopInfo(shopInfo));
    }
}
