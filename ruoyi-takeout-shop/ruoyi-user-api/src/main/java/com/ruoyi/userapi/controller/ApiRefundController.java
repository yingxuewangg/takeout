package com.ruoyi.userapi.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.vo.RefundVo;
import com.ruoyi.userapi.service.ApiRefundService;

/**
 * 小程序端退款接口（走登录态）。
 * 仅整单退款；自助申请仅限主状态 1-3 且无进行中退款（配送中 4 联系商家协商，方案 3.3）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/refund")
public class ApiRefundController extends BaseController
{
    @Autowired
    private ApiRefundService apiRefundService;

    /**
     * 申请退款（整单；防重锁 + 状态链校验；退款金额=订单实付含配送费快照）
     *
     * @param body {orderId, reason(必填), evidenceImages(相对路径数组，可空，≤5 张)}
     */
    @PostMapping("/apply")
    public AjaxResult apply(@RequestBody ApplyBody body)
    {
        apiRefundService.applyRefund(ApiMemberContext.requireMemberId(),
                body.getOrderId(), body.getReason(), body.getEvidenceImages());
        return success();
    }

    /**
     * 订单的退款记录（倒序：审核进度/驳回理由/历史申请）
     */
    @GetMapping("/order/{orderId}")
    public AjaxResult listByOrder(@PathVariable Long orderId)
    {
        List<RefundVo> list = apiRefundService.listByOrder(ApiMemberContext.requireMemberId(), orderId);
        return success(list);
    }

    /** 申请请求体 */
    public static class ApplyBody
    {
        private Long orderId;

        private String reason;

        private List<String> evidenceImages;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public List<String> getEvidenceImages() { return evidenceImages; }
        public void setEvidenceImages(List<String> evidenceImages) { this.evidenceImages = evidenceImages; }
    }
}
