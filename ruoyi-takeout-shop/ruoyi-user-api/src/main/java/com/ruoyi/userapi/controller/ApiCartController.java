package com.ruoyi.userapi.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.vo.CartItemVo;
import com.ruoyi.userapi.domain.vo.CheckoutVo;
import com.ruoyi.userapi.service.ApiCartService;

/**
 * 小程序端购物车接口（走登录态；交易数据不缓存）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/cart")
public class ApiCartController extends BaseController
{
    @Autowired
    private ApiCartService apiCartService;

    /**
     * 购物车明细（含菜品实时价格与有效性标记）
     */
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<CartItemVo> list = apiCartService.getCartList(ApiMemberContext.requireMemberId());
        return success(list);
    }

    /**
     * 加购（同菜品+规格+口味组合合并数量；售罄/下架后端二次校验拦截）
     */
    @PostMapping("/add")
    public AjaxResult add(@RequestBody CartAddBody body)
    {
        apiCartService.addToCart(ApiMemberContext.requireMemberId(),
                body.getGoodsId(), body.getSpecId(), body.getFlavorJson(), body.getQuantity());
        return success();
    }

    /**
     * 修改数量（1-99）
     */
    @PutMapping("/quantity")
    public AjaxResult changeQuantity(@RequestBody QuantityBody body)
    {
        apiCartService.changeQuantity(ApiMemberContext.requireMemberId(), body.getId(), body.getQuantity());
        return success();
    }

    /**
     * 删除单项
     */
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        apiCartService.removeItem(ApiMemberContext.requireMemberId(), id);
        return success();
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    public AjaxResult clear()
    {
        apiCartService.clearCart(ApiMemberContext.requireMemberId());
        return success();
    }

    /**
     * 结算预览（菜品合计/配送费/起送价校验/打烊与失效项拦截标志；不落订单）
     *
     * @param deliveryType 1堂食 2外卖
     * @param tableNo 桌号（堂食必填用于可提交判断）
     */
    @GetMapping("/checkout")
    public AjaxResult checkout(@RequestParam Integer deliveryType,
            @RequestParam(value = "tableNo", required = false) String tableNo)
    {
        CheckoutVo vo = apiCartService.checkout(ApiMemberContext.requireMemberId(), deliveryType, tableNo);
        return success(vo);
    }

    /** 加购请求体 */
    public static class CartAddBody
    {
        private Long goodsId;
        private Long specId;
        private String flavorJson;
        private Integer quantity;

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public Long getSpecId() { return specId; }
        public void setSpecId(Long specId) { this.specId = specId; }

        public String getFlavorJson() { return flavorJson; }
        public void setFlavorJson(String flavorJson) { this.flavorJson = flavorJson; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    /** 数量请求体 */
    public static class QuantityBody
    {
        private Long id;
        private Integer quantity;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
