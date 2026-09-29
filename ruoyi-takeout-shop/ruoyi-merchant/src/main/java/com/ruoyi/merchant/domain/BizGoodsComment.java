package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 商品留言对象 biz_goods_comment。
 * 资格（用户 2026-09-25 变更，覆盖方案 3.1/决策 19 的"不强制已购"）：
 * **仅"买过"该商品的用户可留言**——本人存在包含该商品的合格订单
 * （主状态 1-5、未退款成功 refund_status!=2），且留言必须关联该订单（order_id 必填）。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods_comment")
public class BizGoodsComment implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 留言ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 留言用户ID */
    private Long memberId;

    /** 商品ID */
    private Long goodsId;

    /** 关联订单ID（已购留言：必填；资格校验见类说明） */
    private Long orderId;

    /** 留言内容 */
    private String content;

    /** 留言图片（JSON数组存相对路径） */
    private String images;

    /** 评分（1-5） */
    private Integer score;

    /** 商家回复 */
    private String reply;

    /** 回复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date replyTime;

    /** 状态（0正常 1隐藏/删除） */
    private String status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public Date getReplyTime() { return replyTime; }
    public void setReplyTime(Date replyTime) { this.replyTime = replyTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
