package com.ruoyi.userapi.service;

import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 小程序端商品留言服务接口。
 * 资格（用户 2026-09-25 变更）：**仅"买过"该商品的用户可留言**——
 * 本人存在包含该商品的合格订单（主状态 1-5、refund_status!=2），留言必须关联该订单。
 *
 * @author 阿婆干饭社
 */
public interface ApiCommentService
{
    /**
     * 商品留言分页列表（游客可看；仅正常状态；含用户昵称/头像、"已购"标识、商家回复）
     */
    IPage<CommentVo> listByGoods(Long goodsId, long pageNum, long pageSize);

    /**
     * 发表留言（敏感词拦截 + 已购资格校验 + 关联订单校验）
     *
     * @param images 图片相对路径数组（可空，≤5 张）
     */
    void addComment(Long memberId, Long goodsId, Long orderId, String content, List<String> images, Integer score);

    /**
     * 当前用户可关联该商品的订单列表（"买过"的合格订单：主状态 1-5、refund_status!=2、商品在明细中）
     */
    List<CommentOrderVo> eligibleOrders(Long memberId, Long goodsId);

    /**
     * 商品留言统计（正常留言条数 + 平均分；详情页入口展示用）
     */
    CommentStats statsByGoods(Long goodsId);

    /** 留言条目 VO */
    class CommentVo
    {
        private Long id;
        private Long memberId;
        private String nickname;
        private String avatar;
        private Integer score;
        private String content;
        private List<String> images;
        /** 是否关联订单（已购留言恒为 true） */
        private Boolean hasOrder;
        private String reply;
        private Date replyTime;
        private Date createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }
        public Boolean getHasOrder() { return hasOrder; }
        public void setHasOrder(Boolean hasOrder) { this.hasOrder = hasOrder; }
        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
        public Date getReplyTime() { return replyTime; }
        public void setReplyTime(Date replyTime) { this.replyTime = replyTime; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
    }

    /** 可关联订单 VO */
    class CommentOrderVo
    {
        private Long orderId;
        private String orderNo;
        private Date createTime;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
    }

    /** 留言统计 VO */
    class CommentStats
    {
        private Long count;
        private Double avgScore;

        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
        public Double getAvgScore() { return avgScore; }
        public void setAvgScore(Double avgScore) { this.avgScore = avgScore; }
    }
}
