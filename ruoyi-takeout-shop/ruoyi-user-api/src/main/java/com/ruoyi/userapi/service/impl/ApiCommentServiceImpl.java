package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsComment;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.mapper.BizGoodsCommentMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.userapi.domain.BizMember;
import com.ruoyi.userapi.mapper.BizMemberMapper;
import com.ruoyi.userapi.service.ApiCommentService;
import com.ruoyi.userapi.util.FileUrlBuilder;
import com.ruoyi.userapi.util.SensitiveWordFilter;

/**
 * 小程序端商品留言服务实现。
 * 资格（用户 2026-09-25 变更）：仅"买过"该商品的用户可留言——
 * 本人存在包含该商品的合格订单（主状态 1-5、refund_status!=2），留言必须关联该订单。
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiCommentServiceImpl implements ApiCommentService
{
    private static final int MAX_IMAGES = 5;

    @Autowired
    private BizGoodsCommentMapper commentMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    @Autowired
    private BizMemberMapper memberMapper;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    @Override
    public IPage<CommentVo> listByGoods(Long goodsId, long pageNum, long pageSize)
    {
        LambdaQueryWrapper<BizGoodsComment> wrapper = new LambdaQueryWrapper<BizGoodsComment>()
                .eq(BizGoodsComment::getGoodsId, goodsId)
                .eq(BizGoodsComment::getStatus, "0")
                .orderByDesc(BizGoodsComment::getId);
        IPage<BizGoodsComment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizGoodsComment> comments = page.getRecords();

        List<CommentVo> vos = new ArrayList<>();
        if (!comments.isEmpty())
        {
            // 批量取留言用户（昵称/头像）
            List<Long> memberIds = comments.stream().map(BizGoodsComment::getMemberId).distinct().collect(Collectors.toList());
            Map<Long, BizMember> memberMap = memberMapper.selectBatchIds(memberIds).stream()
                    .collect(Collectors.toMap(BizMember::getId, Function.identity()));
            for (BizGoodsComment comment : comments)
            {
                vos.add(toVo(comment, memberMap.get(comment.getMemberId())));
            }
        }
        Page<CommentVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public void addComment(Long memberId, Long goodsId, Long orderId, String content, List<String> images, Integer score)
    {
        // 1. 基础校验
        if (goodsId == null)
        {
            throw new ServiceException("商品ID不能为空");
        }
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("请填写留言内容");
        }
        if (content.length() > 500)
        {
            throw new ServiceException("留言内容不能超过 500 字");
        }
        if (score == null || score < 1 || score > 5)
        {
            throw new ServiceException("评分需在 1-5 星");
        }
        if (images != null && images.size() > MAX_IMAGES)
        {
            throw new ServiceException("图片最多 " + MAX_IMAGES + " 张");
        }
        // 2. 商品存在且在售
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null)
        {
            throw new ServiceException("商品不存在");
        }
        // 3. 敏感词拦截（词库配置文件、启动加载，方案 3.2）
        if (sensitiveWordFilter.contains(content))
        {
            throw new ServiceException("留言内容包含敏感词，请修改后发布");
        }
        // 4. 已购资格：必须关联订单，且订单合格（本人/主状态1-5/未退款成功/商品在明细中）
        if (orderId == null)
        {
            throw new ServiceException("购买后才能留言，请选择关联订单");
        }
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId))
        {
            throw new ServiceException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() < 1 || order.getStatus() > 5)
        {
            throw new ServiceException("该订单不满足留言条件（未支付或已取消）");
        }
        if (order.getRefundStatus() != null && order.getRefundStatus() == 2)
        {
            throw new ServiceException("该订单已退款，不可留言");
        }
        // 商品必须在订单明细中（对订单内商品留言）
        Long count = orderItemMapper.selectCount(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, orderId)
                .eq(BizOrderItem::getGoodsId, goodsId));
        if (count == 0)
        {
            throw new ServiceException("该订单中不包含此商品，无法关联留言");
        }
        // 5. 入库
        Date now = DateUtils.getNowDate();
        BizGoodsComment comment = new BizGoodsComment();
        comment.setMemberId(memberId);
        comment.setGoodsId(goodsId);
        comment.setOrderId(orderId);
        comment.setContent(content.trim());
        comment.setImages(images == null || images.isEmpty() ? null : JSON.toJSONString(images));
        comment.setScore(score);
        comment.setStatus("0");
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        commentMapper.insert(comment);
    }

    @Override
    public List<CommentOrderVo> eligibleOrders(Long memberId, Long goodsId)
    {
        // 合格订单：本人、主状态 1-5、未退款成功
        List<BizOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getMemberId, memberId)
                .between(BizOrder::getStatus, 1, 5)
                .ne(BizOrder::getRefundStatus, 2)
                .orderByDesc(BizOrder::getId));
        if (orders.isEmpty())
        {
            return List.of();
        }
        // 过滤：商品在订单明细中
        List<Long> eligibleIds = new ArrayList<>();
        Map<Long, List<BizOrderItem>> itemsByOrder = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                        .in(BizOrderItem::getOrderId, orders.stream().map(BizOrder::getId).collect(Collectors.toList()))
                        .eq(BizOrderItem::getGoodsId, goodsId))
                .stream().collect(Collectors.groupingBy(BizOrderItem::getOrderId));
        for (BizOrder order : orders)
        {
            if (itemsByOrder.containsKey(order.getId()))
            {
                eligibleIds.add(order.getId());
            }
        }
        if (eligibleIds.isEmpty())
        {
            return List.of();
        }
        return orders.stream()
                .filter(o -> eligibleIds.contains(o.getId()))
                .map(o -> {
                    CommentOrderVo vo = new CommentOrderVo();
                    vo.setOrderId(o.getId());
                    vo.setOrderNo(o.getOrderNo());
                    vo.setCreateTime(o.getCreateTime());
                    return vo;
                }).collect(Collectors.toList());
    }

    @Override
    public CommentStats statsByGoods(Long goodsId)
    {
        List<BizGoodsComment> comments = commentMapper.selectList(new LambdaQueryWrapper<BizGoodsComment>()
                .eq(BizGoodsComment::getGoodsId, goodsId)
                .eq(BizGoodsComment::getStatus, "0"));
        CommentStats stats = new CommentStats();
        stats.setCount((long) comments.size());
        if (comments.isEmpty())
        {
            stats.setAvgScore(0.0);
        }
        else
        {
            BigDecimal sum = comments.stream()
                    .map(c -> BigDecimal.valueOf(c.getScore() == null ? 5 : c.getScore()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            stats.setAvgScore(sum.divide(BigDecimal.valueOf(comments.size()), 1, RoundingMode.HALF_UP).doubleValue());
        }
        return stats;
    }

    /** 留言实体 -> VO（头像 URL 拼接、图片 URL 拼接、已购标识） */
    private CommentVo toVo(BizGoodsComment comment, BizMember member)
    {
        CommentVo vo = new CommentVo();
        vo.setId(comment.getId());
        vo.setMemberId(comment.getMemberId());
        if (member != null)
        {
            vo.setNickname(StringUtils.isEmpty(member.getNickname()) ? "干饭人" : member.getNickname());
            vo.setAvatar(fileUrlBuilder.build(member.getAvatar()));
        }
        else
        {
            vo.setNickname("已注销用户");
            vo.setAvatar("");
        }
        vo.setScore(comment.getScore());
        vo.setContent(comment.getContent());
        try
        {
            List<String> images = StringUtils.isEmpty(comment.getImages())
                    ? List.of() : JSON.parseArray(comment.getImages(), String.class);
            vo.setImages(images.stream().map(fileUrlBuilder::build).collect(Collectors.toList()));
        }
        catch (Exception e)
        {
            vo.setImages(List.of());
        }
        // 资格规则：留言必关联订单（已购）
        vo.setHasOrder(comment.getOrderId() != null);
        vo.setReply(comment.getReply());
        vo.setReplyTime(comment.getReplyTime());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
