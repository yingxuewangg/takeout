package com.ruoyi.merchant.service;

import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.merchant.domain.BizGoodsComment;

/**
 * 管理端留言管理服务接口（查看含是否关联订单及订单号、回复、隐藏/恢复、删除）
 *
 * @author 阿婆干饭社
 */
public interface MerchantCommentService
{
    /**
     * 留言分页列表（含隐藏；筛选：商品名/是否关联订单）
     */
    IPage<CommentManageVo> listComments(String goodsName, Boolean hasOrder, long pageNum, long pageSize);

    /**
     * 商家回复（可再次编辑覆盖）
     */
    void reply(Long id, String replyContent);

    /**
     * 隐藏违规留言（status -> 1，用户端不可见）
     */
    void hide(Long id);

    /**
     * 恢复显示（status -> 0）
     */
    void show(Long id);

    /**
     * 删除留言（物理删除）
     */
    void remove(Long id);

    /** 管理端留言 VO */
    class CommentManageVo
    {
        private Long id;
        private Long goodsId;
        private String goodsName;
        private Long memberId;
        private String nickname;
        private Long orderId;
        private String orderNo;
        private String content;
        private List<String> images;
        private Integer score;
        private String reply;
        private Date replyTime;
        /** 0正常 1隐藏 */
        private String status;
        private Date createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }
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
    }
}
