package com.ruoyi.merchant.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.merchant.service.StockService;

/**
 * 管理端每日库存管理（T12）。
 * 查看当天各菜品/规格的剩余与已售库存；手动重置当天库存（临时补货）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/stock")
public class MerchantStockController
{
    @Autowired
    private StockService stockService;

    /** 今日库存列表（含菜品名/规格名/限量/已售/剩余/售罄标记） */
    @PreAuthorize("@ss.hasPermi('merchant:stock:list')")
    @GetMapping("/today")
    public AjaxResult today()
    {
        List<StockService.StockView> list = stockService.listTodayStock();
        return AjaxResult.success(list);
    }

    /** 手动重置当天库存（补货）：sold_qty 归零，可指定新限量值 */
    @PreAuthorize("@ss.hasPermi('merchant:stock:reset')")
    @PutMapping("/reset")
    public AjaxResult reset(@RequestParam Long goodsId,
                            @RequestParam(required = false) Long specId,
                            @RequestParam(required = false) Integer limitQty)
    {
        stockService.resetStock(goodsId, specId, limitQty);
        return AjaxResult.success();
    }
}
