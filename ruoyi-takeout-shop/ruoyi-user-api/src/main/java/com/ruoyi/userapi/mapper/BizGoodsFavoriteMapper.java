package com.ruoyi.userapi.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.userapi.domain.BizGoodsFavorite;
import com.ruoyi.userapi.domain.vo.FavoriteItemVo;

/**
 * 用户收藏菜品 Mapper（T20 子项④）。
 * 分页联查走 XML（需要 join biz_goods 取当前状态）；增删查改用 MyBatis-Plus BaseMapper。
 *
 * @author 阿婆干饭社
 */
@Mapper
public interface BizGoodsFavoriteMapper extends BaseMapper<BizGoodsFavorite>
{
    /**
     * 收藏分页列表（联查菜品当前状态；菜品被物理删除的收藏行仍返回但 onSale=null 由服务层标记）
     */
    List<FavoriteItemVo> selectFavoritePage(@Param("memberId") Long memberId,
                                            @Param("offset") long offset,
                                            @Param("limit") long limit);

    /** 收藏总数 */
    long countFavorite(@Param("memberId") Long memberId);
}
