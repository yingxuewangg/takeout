package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 购物车对象 biz_cart（规格/口味存快照，不随菜品修改变化；交易数据不缓存）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_cart")
public class BizCart implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 购物车记录ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long memberId;

    /** 菜品ID */
    private Long goodsId;

    /** 规格ID（无规格为null） */
    private Long specId;

    /** 规格名快照 */
    private String specName;

    /** 口味快照（规范化 JSON 数组字符串，如 [{"name":"辣度","values":["微辣"]}]） */
    private String flavorJson;

    /** 数量（1-99） */
    private Integer quantity;

    private Date createTime;

    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public Long getSpecId() { return specId; }
    public void setSpecId(Long specId) { this.specId = specId; }

    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }

    public String getFlavorJson() { return flavorJson; }
    public void setFlavorJson(String flavorJson) { this.flavorJson = flavorJson; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
