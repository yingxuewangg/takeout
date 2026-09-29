package com.ruoyi.userapi.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.userapi.domain.BizGoodsFavorite;
import com.ruoyi.userapi.domain.vo.FavoriteItemVo;
import com.ruoyi.userapi.mapper.BizGoodsFavoriteMapper;
import com.ruoyi.userapi.service.ApiFavoriteService;
import com.ruoyi.userapi.util.FileUrlBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 用户收藏菜品服务实现（T20 子项④）。
 * 唯一索引 uk_member_goods 兜底防重；toggle 依赖"查-插/删"，并发重复点击最多触发唯一索引冲突，
 * 结果仍是"已收藏"状态，无副作用。
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiFavoriteServiceImpl implements ApiFavoriteService
{
    /** 商品在售状态（与 biz_goods.status 字典一致：'1'在售） */
    private static final String GOODS_ON_SALE = "1";

    @Autowired
    private BizGoodsFavoriteMapper favoriteMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    @Override
    public boolean toggle(Long memberId, Long goodsId)
    {
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null)
        {
            throw new ServiceException("商品不存在");
        }
        BizGoodsFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<BizGoodsFavorite>()
                .eq(BizGoodsFavorite::getMemberId, memberId)
                .eq(BizGoodsFavorite::getGoodsId, goodsId));
        if (existing != null)
        {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        BizGoodsFavorite favorite = new BizGoodsFavorite();
        favorite.setMemberId(memberId);
        favorite.setGoodsId(goodsId);
        favorite.setCreateTime(DateUtils.getNowDate());
        try
        {
            favoriteMapper.insert(favorite);
        }
        catch (org.springframework.dao.DuplicateKeyException e)
        {
            // 并发重复点击：唯一索引兜底，视为已收藏
        }
        return true;
    }

    @Override
    public boolean isFavorited(Long memberId, Long goodsId)
    {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<BizGoodsFavorite>()
                .eq(BizGoodsFavorite::getMemberId, memberId)
                .eq(BizGoodsFavorite::getGoodsId, goodsId)) > 0;
    }

    @Override
    public List<Long> favoritedGoodsIds(Long memberId)
    {
        return favoriteMapper.selectList(new LambdaQueryWrapper<BizGoodsFavorite>()
                        .eq(BizGoodsFavorite::getMemberId, memberId))
                .stream().map(BizGoodsFavorite::getGoodsId).toList();
    }

    @Override
    public Page<FavoriteItemVo> pageFavorites(Long memberId, long pageNum, long pageSize)
    {
        long total = favoriteMapper.countFavorite(memberId);
        long safePage = Math.max(pageNum, 1);
        long safeSize = Math.min(Math.max(pageSize, 1), 50);
        List<FavoriteItemVo> rows = total > 0
                ? favoriteMapper.selectFavoritePage(memberId, (safePage - 1) * safeSize, safeSize)
                : List.of();
        for (FavoriteItemVo row : rows)
        {
            row.setImage(fileUrlBuilder.build(row.getImage()));
        }
        Page<FavoriteItemVo> page = new Page<>(safePage, safeSize, total);
        page.setRecords(rows);
        return page;
    }
}
