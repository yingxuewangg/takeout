package com.ruoyi.userapi.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户收藏菜品对象 biz_goods_favorite（T20 子项④）。
 * member_id + goods_id 唯一索引防重复收藏；取消收藏物理删除。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods_favorite")
public class BizGoodsFavorite implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 收藏ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long memberId;

    /** 菜品ID */
    private Long goodsId;

    /** 收藏时间 */
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
