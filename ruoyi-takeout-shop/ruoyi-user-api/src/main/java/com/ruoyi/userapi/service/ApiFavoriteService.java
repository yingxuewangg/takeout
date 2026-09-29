package com.ruoyi.userapi.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.userapi.domain.vo.FavoriteItemVo;

/**
 * 用户收藏菜品服务（T20 子项④）
 *
 * @author 阿婆干饭社
 */
public interface ApiFavoriteService
{
    /**
     * 收藏/取消收藏（切换），返回切换后是否已收藏
     *
     * @param goodsId 菜品ID（须存在，下架/售罄商品也可收藏，但收藏列表会置灰展示）
     */
    boolean toggle(Long memberId, Long goodsId);

    /** 是否已收藏 */
    boolean isFavorited(Long memberId, Long goodsId);

    /** 当前用户收藏的菜品ID集合（菜单/详情❤标记用） */
    List<Long> favoritedGoodsIds(Long memberId);

    /** 收藏分页列表（联查菜品当前状态，下架/失效置灰展示） */
    Page<FavoriteItemVo> pageFavorites(Long memberId, long pageNum, long pageSize);
}
