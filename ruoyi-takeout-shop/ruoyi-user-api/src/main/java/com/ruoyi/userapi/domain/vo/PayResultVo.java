package com.ruoyi.userapi.domain.vo;

import java.util.Map;

/**
 * 统一支付结果 VO（三期）。
 *
 * channel=mock 时 payParams 为 null，前端直接跳支付成功页（原有模拟流程）；
 * channel=wechat 时 payParams 为小程序 wx.requestPayment 所需参数：
 * appId / timeStamp / nonceStr / package / signType / paySign。
 *
 * @author 阿婆干饭社
 */
public class PayResultVo
{
    /** 支付渠道：mock=模拟支付（直接成功），wechat=微信 JSAPI 支付 */
    private String channel;

    /** 订单号（out_trade_no 同订单号） */
    private String orderNo;

    /** 微信调起参数（仅 channel=wechat 时返回） */
    private Map<String, String> payParams;

    public static PayResultVo mock(String orderNo)
    {
        PayResultVo vo = new PayResultVo();
        vo.setChannel("mock");
        vo.setOrderNo(orderNo);
        return vo;
    }

    public static PayResultVo wechat(String orderNo, Map<String, String> payParams)
    {
        PayResultVo vo = new PayResultVo();
        vo.setChannel("wechat");
        vo.setOrderNo(orderNo);
        vo.setPayParams(payParams);
        return vo;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public Map<String, String> getPayParams()
    {
        return payParams;
    }

    public void setPayParams(Map<String, String> payParams)
    {
        this.payParams = payParams;
    }
}
