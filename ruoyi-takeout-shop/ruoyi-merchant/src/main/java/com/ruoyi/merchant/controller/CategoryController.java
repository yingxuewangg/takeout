package com.ruoyi.merchant.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.merchant.domain.BizCategory;
import com.ruoyi.merchant.service.CategoryService;

/**
 * 菜品分类管理
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/category")
public class CategoryController extends BaseController
{
    @Autowired
    private CategoryService categoryService;

    /**
     * 查询分类列表（不分页，按 sort 升序）
     */
    @PreAuthorize("@ss.hasPermi('goods:category:list')")
    @GetMapping("/list")
    public AjaxResult list(BizCategory query)
    {
        List<BizCategory> list = categoryService.selectCategoryList(query);
        return success(list);
    }

    /**
     * 查询分类详情
     */
    @PreAuthorize("@ss.hasPermi('goods:category:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(categoryService.selectCategoryById(id));
    }

    /**
     * 新增分类
     */
    @PreAuthorize("@ss.hasPermi('goods:category:add')")
    @Log(title = "菜品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizCategory category)
    {
        category.setCreateBy(SecurityUtils.getUsername());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改分类
     */
    @PreAuthorize("@ss.hasPermi('goods:category:edit')")
    @Log(title = "菜品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizCategory category)
    {
        category.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 删除分类（分类下存在菜品时拒绝）
     */
    @PreAuthorize("@ss.hasPermi('goods:category:remove')")
    @Log(title = "菜品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(categoryService.deleteCategoryByIds(ids));
    }
}
