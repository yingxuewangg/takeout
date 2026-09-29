package com.ruoyi.userapi.service;

import java.util.List;
import com.ruoyi.userapi.domain.vo.CartItemVo;
import com.ruoyi.userapi.domain.vo.CheckoutVo;

/**
 * 购物车服务接口（交易数据不缓存，直接查库）
 *
 * @author 阿婆干饭社
 */
public interface ApiCartService
{
    /**
     * 当前用户购物车明细（联查菜品实时信息，失效项标注）
     */
    List<CartItemVo> getCartList(Long memberId);

    /**
     * 加购：同一菜品+同一规格+同一口味组合合并数量；
     * 后端二次校验：在售、未售罄、规格归属、数量 1-99
     *
     * @param flavorJson 口味快照 JSON 数组字符串（可空）
     */
    void addToCart(Long memberId, Long goodsId, Long specId, String flavorJson, Integer quantity);

    /**
     * 修改数量（1-99）
     */
    void changeQuantity(Long memberId, Long id, Integer quantity);

    /**
     * 删除单项（归属校验）
     */
    void removeItem(Long memberId, Long id);

    /**
     * 清空当前用户购物车
     */
    void clearCart(Long memberId);

    /**
     * 结算预览（T4 展示与校验，不落订单）：
     * 菜品合计、配送费（外卖=店铺当前值）、起送价校验、打烊/失效项拦截标志
     *
     * @param deliveryType 1堂食 2外卖
     * @param tableNo 桌号（堂食）
     */
    CheckoutVo checkout(Long memberId, Integer deliveryType, String tableNo);
}
