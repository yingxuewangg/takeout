package com.ruoyi.merchant.service.impl;

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
import com.ruoyi.merchant.mapper.BizGoodsCommentMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.service.MerchantCommentService;

/**
 * 管理端留言管理服务实现。
 * 留言者昵称属用户专属表数据（biz_member，归属 userapi 模块），merchant 不能反向引用；
 * 管理端展示"用户#ID"占位（留言内容审核场景够用，方案未要求管理端显示昵称）。
 *
 * @author 阿婆干饭社
 */
@Service
public class MerchantCommentServiceImpl implements MerchantCommentService
{
    @Autowired
    private BizGoodsCommentMapper commentMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizOrderMapper orderMapper;

    @Override
    public IPage<CommentManageVo> listComments(String goodsName, Boolean hasOrder, long pageNum, long pageSize)
    {
        // 商品名筛选：先查命中的商品ID
        List<Long> goodsIds = null;
        if (StringUtils.isNotEmpty(goodsName))
        {
            goodsIds = goodsMapper.selectList(new LambdaQueryWrapper<BizGoods>()
                            .like(BizGoods::getName, goodsName))
                    .stream().map(BizGoods::getId).collect(Collectors.toList());
            if (goodsIds.isEmpty())
            {
                Page<CommentManageVo> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(List.of());
                return empty;
            }
        }
        LambdaQueryWrapper<BizGoodsComment> wrapper = new LambdaQueryWrapper<BizGoodsComment>()
                .in(goodsIds != null, BizGoodsComment::getGoodsId, goodsIds)
                .isNotNull(hasOrder != null && hasOrder, BizGoodsComment::getOrderId)
                .isNull(hasOrder != null && !hasOrder, BizGoodsComment::getOrderId)
                .orderByDesc(BizGoodsComment::getId);
        IPage<BizGoodsComment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizGoodsComment> comments = page.getRecords();

        Map<Long, BizGoods> goodsMap = comments.isEmpty() ? Map.of()
                : goodsMapper.selectBatchIds(comments.stream().map(BizGoodsComment::getGoodsId).distinct().collect(Collectors.toList()))
                        .stream().collect(Collectors.toMap(BizGoods::getId, Function.identity()));
        Map<Long, BizOrder> orderMap = comments.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<BizOrder>()
                        .in(BizOrder::getId, comments.stream().map(BizGoodsComment::getOrderId).filter(java.util.Objects::nonNull).collect(Collectors.toList())))
                        .stream().collect(Collectors.toMap(BizOrder::getId, Function.identity(), (a, b) -> a));

        List<CommentManageVo> vos = new ArrayList<>();
        for (BizGoodsComment comment : comments)
        {
            CommentManageVo vo = new CommentManageVo();
            vo.setId(comment.getId());
            vo.setGoodsId(comment.getGoodsId());
            BizGoods goods = goodsMap.get(comment.getGoodsId());
            vo.setGoodsName(goods == null ? "-" : goods.getName());
            vo.setMemberId(comment.getMemberId());
            // 昵称由前端按需显示为"用户#ID"占位（管理端关注内容本身）；如需昵称可后续加联表查询
            vo.setNickname(comment.getMemberId() == null ? "-" : "用户#" + comment.getMemberId());
            vo.setOrderId(comment.getOrderId());
            BizOrder order = comment.getOrderId() == null ? null : orderMap.get(comment.getOrderId());
            vo.setOrderNo(order == null ? "-" : order.getOrderNo());
            vo.setContent(comment.getContent());
            try
            {
                List<String> images = StringUtils.isEmpty(comment.getImages())
                        ? List.of() : JSON.parseArray(comment.getImages(), String.class);
                vo.setImages(images);
            }
            catch (Exception e)
            {
                vo.setImages(List.of());
            }
            vo.setScore(comment.getScore());
            vo.setReply(comment.getReply());
            vo.setReplyTime(comment.getReplyTime());
            vo.setStatus(comment.getStatus());
            vo.setCreateTime(comment.getCreateTime());
            vos.add(vo);
        }
        Page<CommentManageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public void reply(Long id, String replyContent)
    {
        if (StringUtils.isEmpty(replyContent))
        {
            throw new ServiceException("请填写回复内容");
        }
        BizGoodsComment comment = getExisting(id);
        comment.setReply(replyContent.trim());
        comment.setReplyTime(DateUtils.getNowDate());
        comment.setUpdateTime(DateUtils.getNowDate());
        commentMapper.updateById(comment);
    }

    @Override
    public void hide(Long id)
    {
        changeStatus(id, "1");
    }

    @Override
    public void show(Long id)
    {
        changeStatus(id, "0");
    }

    @Override
    public void remove(Long id)
    {
        getExisting(id);
        commentMapper.deleteById(id);
    }

    private void changeStatus(Long id, String status)
    {
        BizGoodsComment comment = getExisting(id);
        comment.setStatus(status);
        comment.setUpdateTime(DateUtils.getNowDate());
        commentMapper.updateById(comment);
    }

    private BizGoodsComment getExisting(Long id)
    {
        BizGoodsComment comment = commentMapper.selectById(id);
        if (comment == null)
        {
            throw new ServiceException("留言不存在");
        }
        return comment;
    }
}
