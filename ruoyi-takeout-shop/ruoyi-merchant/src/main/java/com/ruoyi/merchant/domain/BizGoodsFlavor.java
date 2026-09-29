package com.ruoyi.merchant.domain;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 菜品口味选项对象 biz_goods_flavor（可多组、单选/多选）
 * options 为 JSON 列，实体用 String 读写 JSON 数组字符串（如 ["微辣","中辣","特辣"]）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods_flavor")
public class BizGoodsFlavor implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 口味组ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 菜品ID */
    private Long goodsId;

    /** 口味组名（如辣度/温度） */
    private String name;

    /** 选项列表（JSON数组字符串，如 ["微辣","中辣","特辣"]） */
    private String options;

    /** 选择方式（0单选 1多选） */
    private String selectType;

    /** 显示顺序 */
    private Integer sort;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getSelectType() { return selectType; }
    public void setSelectType(String selectType) { this.selectType = selectType; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
