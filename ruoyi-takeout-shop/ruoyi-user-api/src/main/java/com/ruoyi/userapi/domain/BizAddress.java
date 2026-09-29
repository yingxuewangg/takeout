package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户地址簿对象 biz_address（订单保存地址快照，本表只维护地址簿）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_address")
public class BizAddress implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 地址ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long memberId;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认地址（0否 1是） */
    private Integer isDefault;

    private Date createTime;

    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
