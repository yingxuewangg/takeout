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
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.BizAddress;
import com.ruoyi.userapi.service.ApiAddressService;

/**
 * 小程序端地址簿接口（走登录态；订单保存地址快照在 T5 下单时实现）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/address")
public class ApiAddressController extends BaseController
{
    @Autowired
    private ApiAddressService apiAddressService;

    /**
     * 地址列表（默认地址排最前）
     */
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<BizAddress> list = apiAddressService.getAddressList(ApiMemberContext.requireMemberId());
        return success(list);
    }

    /**
     * 新增地址（首个地址自动设为默认）
     */
    @PostMapping
    public AjaxResult add(@RequestBody BizAddress address)
    {
        apiAddressService.addAddress(ApiMemberContext.requireMemberId(), address);
        return success();
    }

    /**
     * 修改地址
     */
    @PutMapping
    public AjaxResult update(@RequestBody BizAddress address)
    {
        apiAddressService.updateAddress(ApiMemberContext.requireMemberId(), address);
        return success();
    }

    /**
     * 删除地址
     */
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        apiAddressService.deleteAddress(ApiMemberContext.requireMemberId(), id);
        return success();
    }

    /**
     * 设为默认地址
     */
    @PutMapping("/default/{id}")
    public AjaxResult setDefault(@PathVariable Long id)
    {
        apiAddressService.setDefault(ApiMemberContext.requireMemberId(), id);
        return success();
    }
}
