package com.ruoyi.userapi.service;

import java.util.List;
import com.ruoyi.userapi.domain.BizAddress;

/**
 * 用户地址簿服务接口
 *
 * @author 阿婆干饭社
 */
public interface ApiAddressService
{
    /**
     * 地址列表（默认地址排最前）
     */
    List<BizAddress> getAddressList(Long memberId);

    /**
     * 默认地址（无默认取第一条，可空）
     */
    BizAddress getDefaultAddress(Long memberId);

    /**
     * 新增地址（首个地址自动设为默认；设为默认时清除原默认）
     */
    void addAddress(Long memberId, BizAddress address);

    /**
     * 修改地址（归属校验；设为默认时清除原默认）
     */
    void updateAddress(Long memberId, BizAddress address);

    /**
     * 删除地址（归属校验；删除默认地址后自动将剩余第一条设为默认）
     */
    void deleteAddress(Long memberId, Long id);

    /**
     * 设为默认地址
     */
    void setDefault(Long memberId, Long id);
}
