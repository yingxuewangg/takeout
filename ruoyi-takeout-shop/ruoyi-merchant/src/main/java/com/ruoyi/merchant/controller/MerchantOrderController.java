package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.service.MerchantOrderService;

/**
 * 管理端订单管理：列表/详情/流转操作（接单/出餐/开始配送/确认完成）。
 * 流转严格按方案 3.4 状态流转操作表执行，条件更新幂等；每次流转发送 order-status-changed 消息。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/order")
public class MerchantOrderController extends BaseController
{
    @Autowired
    private MerchantOrderService merchantOrderService;

    /**
     * 订单分页列表（订单号/状态/履约方式筛选）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer deliveryType,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<BizOrder> page = merchantOrderService.listOrders(orderNo, status, deliveryType, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 订单详情（含明细快照、用户联系电话）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(merchantOrderService.getOrder(id));
    }

    /**
     * 接单（1 -> 2）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:accept')")
    @Log(title = "订单接单", businessType = BusinessType.UPDATE)
    @PutMapping("/accept/{id}")
    public AjaxResult accept(@PathVariable Long id)
    {
        merchantOrderService.accept(id);
        return success();
    }

    /**
     * 出餐（2 -> 3）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:ready')")
    @Log(title = "订单出餐", businessType = BusinessType.UPDATE)
    @PutMapping("/ready/{id}")
    public AjaxResult ready(@PathVariable Long id)
    {
        merchantOrderService.ready(id);
        return success();
    }

    /**
     * 骑手已取餐（3 -> 4，仅外卖；T18 语义明确，接口路径不变）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:deliver')")
    @Log(title = "订单骑手已取餐", businessType = BusinessType.UPDATE)
    @PutMapping("/deliver/{id}")
    public AjaxResult deliver(@PathVariable Long id)
    {
        merchantOrderService.deliver(id);
        return success();
    }

    /**
     * 确认完成（堂食 3 -> 5；外卖 4 -> 5）
     */
    @PreAuthorize("@ss.hasPermi('merchant:order:complete')")
    @Log(title = "订单确认完成", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{id}")
    public AjaxResult complete(@PathVariable Long id)
    {
        merchantOrderService.complete(id);
        return success();
    }
}
