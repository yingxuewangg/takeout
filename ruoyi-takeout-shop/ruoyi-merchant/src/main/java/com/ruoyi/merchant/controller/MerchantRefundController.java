package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import com.ruoyi.merchant.service.MerchantRefundService;

/**
 * 管理端退款审核：列表（含用户联系电话/退款金额）/详情/通过（模拟退款成功）/驳回。
 * 待审核数量角标接口供页面 badge 提醒。配送中订单不可用户自助申请，故不存在其退款单。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/refund")
public class MerchantRefundController extends BaseController
{
    @Autowired
    private MerchantRefundService merchantRefundService;

    /**
     * 退款单分页列表（auditStatus：null 全部 / 0 待审核 / 1 已通过 / 2 已驳回）
     */
    @PreAuthorize("@ss.hasPermi('merchant:refund:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<MerchantRefundService.RefundManageVo> page =
                merchantRefundService.listRefunds(auditStatus, orderNo, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 待审核数量（页面 badge 角标提醒）
     */
    @PreAuthorize("@ss.hasPermi('merchant:refund:list')")
    @GetMapping("/pending-count")
    public AjaxResult pendingCount()
    {
        AjaxResult ajax = success();
        ajax.put("count", merchantRefundService.pendingCount());
        return ajax;
    }

    /**
     * 退款单详情（凭证大图）
     */
    @PreAuthorize("@ss.hasPermi('merchant:refund:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(merchantRefundService.getRefund(id));
    }

    /**
     * 审核通过（模拟退款成功，订单置终态；条件更新幂等）
     */
    @PreAuthorize("@ss.hasPermi('merchant:refund:approve')")
    @Log(title = "退款审核通过", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{id}")
    public AjaxResult approve(@PathVariable Long id)
    {
        merchantRefundService.approve(id);
        return success();
    }

    /**
     * 驳回（理由必填、用户可见；订单流转恢复、用户可再次申请）
     */
    @PreAuthorize("@ss.hasPermi('merchant:refund:reject')")
    @Log(title = "退款审核驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{id}")
    public AjaxResult reject(@PathVariable Long id, @RequestBody RejectBody body)
    {
        merchantRefundService.reject(id, body.getReason());
        return success();
    }

    /** 驳回请求体 */
    public static class RejectBody
    {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
