package com.ruoyi.userapi.service.impl;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.userapi.domain.BizAddress;
import com.ruoyi.userapi.mapper.BizAddressMapper;
import com.ruoyi.userapi.service.ApiAddressService;

/**
 * 用户地址簿服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiAddressServiceImpl implements ApiAddressService
{
    /** 手机号基础格式 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Autowired
    private BizAddressMapper addressMapper;

    @Override
    public List<BizAddress> getAddressList(Long memberId)
    {
        return addressMapper.selectList(new LambdaQueryWrapper<BizAddress>()
                .eq(BizAddress::getMemberId, memberId)
                .orderByDesc(BizAddress::getIsDefault)
                .orderByDesc(BizAddress::getId));
    }

    @Override
    public BizAddress getDefaultAddress(Long memberId)
    {
        BizAddress def = addressMapper.selectOne(new LambdaQueryWrapper<BizAddress>()
                .eq(BizAddress::getMemberId, memberId)
                .eq(BizAddress::getIsDefault, 1)
                .last("limit 1"));
        if (def != null)
        {
            return def;
        }
        // 无默认取第一条
        return addressMapper.selectOne(new LambdaQueryWrapper<BizAddress>()
                .eq(BizAddress::getMemberId, memberId)
                .orderByDesc(BizAddress::getId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public void addAddress(Long memberId, BizAddress address)
    {
        validate(address);
        address.setId(null);
        address.setMemberId(memberId);
        // 首个地址自动设为默认
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<BizAddress>().eq(BizAddress::getMemberId, memberId));
        boolean makeDefault = count == 0 || (address.getIsDefault() != null && address.getIsDefault() == 1);
        if (makeDefault && count > 0)
        {
            clearDefault(memberId);
        }
        address.setIsDefault(makeDefault ? 1 : 0);
        Date now = DateUtils.getNowDate();
        address.setCreateTime(now);
        address.setUpdateTime(now);
        addressMapper.insert(address);
    }

    @Override
    @Transactional
    public void updateAddress(Long memberId, BizAddress address)
    {
        if (address.getId() == null)
        {
            throw new ServiceException("地址ID不能为空");
        }
        BizAddress existing = getOwnedAddress(memberId, address.getId());
        validate(address);
        if (address.getIsDefault() != null && address.getIsDefault() == 1 && existing.getIsDefault() != 1)
        {
            clearDefault(memberId);
        }
        address.setMemberId(memberId);
        address.setUpdateTime(DateUtils.getNowDate());
        addressMapper.updateById(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long memberId, Long id)
    {
        BizAddress existing = getOwnedAddress(memberId, id);
        addressMapper.deleteById(id);
        // 删除的是默认地址：剩余第一条自动顶为默认
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1)
        {
            BizAddress first = addressMapper.selectOne(new LambdaQueryWrapper<BizAddress>()
                    .eq(BizAddress::getMemberId, memberId)
                    .orderByDesc(BizAddress::getId)
                    .last("limit 1"));
            if (first != null)
            {
                first.setIsDefault(1);
                addressMapper.updateById(first);
            }
        }
    }

    @Override
    @Transactional
    public void setDefault(Long memberId, Long id)
    {
        getOwnedAddress(memberId, id);
        clearDefault(memberId);
        BizAddress address = new BizAddress();
        address.setId(id);
        address.setIsDefault(1);
        address.setUpdateTime(DateUtils.getNowDate());
        addressMapper.updateById(address);
    }

    /** 归属校验：只能操作自己的地址 */
    private BizAddress getOwnedAddress(Long memberId, Long id)
    {
        BizAddress address = addressMapper.selectById(id);
        if (address == null || !address.getMemberId().equals(memberId))
        {
            throw new ServiceException("地址不存在");
        }
        return address;
    }

    private void clearDefault(Long memberId)
    {
        addressMapper.update(null, new LambdaUpdateWrapper<BizAddress>()
                .eq(BizAddress::getMemberId, memberId)
                .eq(BizAddress::getIsDefault, 1)
                .set(BizAddress::getIsDefault, 0));
    }

    /** 字段校验：联系人/电话/详址必填，电话基础格式 */
    private void validate(BizAddress address)
    {
        if (StringUtils.isEmpty(address.getContactName()))
        {
            throw new ServiceException("联系人不能为空");
        }
        if (StringUtils.isEmpty(address.getContactPhone()) || !PHONE_PATTERN.matcher(address.getContactPhone()).matches())
        {
            throw new ServiceException("请输入正确的手机号");
        }
        if (StringUtils.isEmpty(address.getDetail()))
        {
            throw new ServiceException("详细地址不能为空");
        }
        address.setContactName(address.getContactName().trim());
        address.setDetail(address.getDetail().trim());
    }
}
