package com.ruoyi.merchant.service;

import java.util.List;
import com.ruoyi.merchant.domain.BizCategory;

/**
 * 菜品分类 服务层
 *
 * @author 阿婆干饭社
 */
public interface CategoryService
{
    /**
     * 查询分类列表（按 sort 升序）
     */
    List<BizCategory> selectCategoryList(BizCategory query);

    /**
     * 查询分类详情
     */
    BizCategory selectCategoryById(Long id);

    /**
     * 新增分类
     *
     * @return 影响行数
     */
    int insertCategory(BizCategory category);

    /**
     * 修改分类
     *
     * @return 影响行数
     */
    int updateCategory(BizCategory category);

    /**
     * 批量删除分类（分类下存在菜品时拒绝删除）
     *
     * @return 影响行数
     */
    int deleteCategoryByIds(Long[] ids);
}
