package com.ruoyi.userapi.service;

import java.util.List;
import com.ruoyi.userapi.domain.vo.RefundVo;

/**
 * 小程序端退款服务接口（方案 3.3：仅整单退款；用户自助申请仅限主状态 1-3 且无进行中退款）
 *
 * @author 阿婆干饭社
 */
public interface ApiRefundService
{
    /**
     * 申请退款：
     * 防重锁（Controller 层）+ 校验（订单归属/主状态 1-3/无进行中退款/理由必填）+
     * 生成退款单（refund_amount=订单实付含配送费快照）+ 订单 refund_status 0->1（主状态不变、流转暂停）
     */
    void applyRefund(Long memberId, Long orderId, String reason, List<String> evidenceImages);

    /**
     * 订单的退款记录（倒序，供用户查看审核进度/驳回理由/历史申请）
     */
    List<RefundVo> listByOrder(Long memberId, Long orderId);
}
