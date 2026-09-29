package com.ruoyi.userapi.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.vo.OrderCreateVo;
import com.ruoyi.userapi.domain.vo.OrderDetailVo;
import com.ruoyi.userapi.domain.vo.OrderListVo;
import com.ruoyi.userapi.service.ApiOrderService;

/**
 * 小程序端订单接口（走登录态）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/order")
public class ApiOrderController extends BaseController
{
    @Autowired
    private ApiOrderService apiOrderService;

    /**
     * 创建订单（从购物车整单提交；成功后购物车清空，返回收银台所需信息）
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody ApiOrderService.OrderCreateBody body)
    {
        OrderCreateVo vo = apiOrderService.createOrder(ApiMemberContext.requireMemberId(), body);
        return success(vo);
    }

    /**
     * 订单详情（归属校验；收银台/支付成功页/订单详情页展示；含退款状态供"退款优先"展示）
     */
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        OrderDetailVo vo = apiOrderService.getOrderDetail(ApiMemberContext.requireMemberId(), id);
        return success(vo);
    }

    /**
     * 订单列表（分页；status 可选筛选：0待支付 1待接单 2制作中 3待取餐 4配送中 5已完成 6已取消）
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<OrderListVo> page = apiOrderService.listOrders(ApiMemberContext.requireMemberId(), status, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 取消订单（仅待支付可取消，条件更新幂等）
     */
    @PutMapping("/cancel/{id}")
    public AjaxResult cancel(@PathVariable Long id)
    {
        apiOrderService.cancelOrder(ApiMemberContext.requireMemberId(), id);
        return success();
    }

    /**
     * 再次购买（T13）：把该订单明细逐项加入购物车，返回成功/跳过明细与原因；
     * 展示条件（前端按钮）与后端拦截一致：主状态 1-5 且未退款成功。
     */
    @PostMapping("/{orderId}/repurchase")
    public AjaxResult repurchase(@PathVariable Long orderId)
    {
        return success(apiOrderService.repurchase(ApiMemberContext.requireMemberId(), orderId));
    }
}
