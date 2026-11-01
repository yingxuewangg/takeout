package com.ruoyi.merchant.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.ruoyi.common.exception.ServiceException;

/**
 * 支付金额工具（三期，金额换算单点）：元（BigDecimal，库内口径）与分（long，微信 APIv3 口径）互转。
 * 全部走 BigDecimal 精确运算，杜绝 double 误差；转换出现精度损耗（超出两位小数）直接失败。
 *
 * @author 阿婆干饭社
 */
public class PayAmountUtils
{
    private PayAmountUtils()
    {
    }

    /**
     * 元转分：multiply(100) 后必须为整数值，否则抛出异常（金额口径错误，禁止静默截断）
     *
     * @param yuan 元金额（库内 decimal(10,2) 口径）
     * @return 分
     */
    public static long toCents(BigDecimal yuan)
    {
        if (yuan == null)
        {
            throw new ServiceException("支付金额不能为空");
        }
        BigDecimal cents = yuan.multiply(new BigDecimal("100"));
        try
        {
            return cents.intValueExact();
        }
        catch (ArithmeticException e)
        {
            throw new ServiceException("支付金额精度异常（超过两位小数）：" + yuan.toPlainString());
        }
    }

    /**
     * 分转元（回调/查单金额比对用），保留两位小数
     *
     * @param cents 分
     * @return 元金额（两位小数）
     */
    public static BigDecimal centsToYuan(Long cents)
    {
        if (cents == null)
        {
            throw new ServiceException("微信返回金额为空");
        }
        return BigDecimal.valueOf(cents).divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY);
    }
}
