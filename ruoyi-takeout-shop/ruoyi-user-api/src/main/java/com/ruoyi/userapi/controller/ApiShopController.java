package com.ruoyi.userapi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.service.ApiShopService;

/**
 * 小程序端店铺信息接口（游客可浏览）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/shop")
public class ApiShopController extends BaseController
{
    @Autowired
    private ApiShopService apiShopService;

    /**
     * 店铺信息（含营业状态/配送费/起送价，走 takeout:shop:1 缓存）
     */
    @GetMapping("/info")
    public AjaxResult info()
    {
        return success(apiShopService.getShopInfo());
    }
}
