package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.service.GoodsService;

/**
 * 菜品管理（含规格、口味级联维护与菜品级售罄标记）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/goods")
public class GoodsController extends BaseController
{
    @Autowired
    private GoodsService goodsService;

    /**
     * 分页查询菜品列表
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:list')")
    @GetMapping("/list")
    public TableDataInfo list(BizGoods query, @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<BizGoods> page = goodsService.selectGoodsPage(new Page<>(pageNum, pageSize), query);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 查询菜品详情（级联带出规格、口味）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(goodsService.selectGoodsById(id));
    }

    /**
     * 新增菜品（级联保存规格、口味）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:add')")
    @Log(title = "菜品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizGoods goods)
    {
        goods.setCreateBy(SecurityUtils.getUsername());
        return toAjax(goodsService.insertGoods(goods));
    }

    /**
     * 修改菜品（规格、口味先删后插）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:edit')")
    @Log(title = "菜品管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizGoods goods)
    {
        goods.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(goodsService.updateGoods(goods));
    }

    /**
     * 批量删除菜品（级联删除规格、口味）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:remove')")
    @Log(title = "菜品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(goodsService.deleteGoodsByIds(ids));
    }

    /**
     * 上架/下架（1在售 0下架）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:shelf')")
    @Log(title = "菜品上下架", businessType = BusinessType.UPDATE)
    @PutMapping("/status/{id}/{status}")
    public AjaxResult changeStatus(@PathVariable Long id, @PathVariable String status)
    {
        return toAjax(goodsService.changeGoodsStatus(id, status));
    }

    /**
     * 批量设置售罄标记（菜品级：1售罄 0取消售罄）
     */
    @PreAuthorize("@ss.hasPermi('goods:goods:soldOut')")
    @Log(title = "菜品售罄标记", businessType = BusinessType.UPDATE)
    @PutMapping("/soldOut")
    public AjaxResult changeSoldOut(@RequestParam Long[] ids, @RequestParam String soldOut)
    {
        return toAjax(goodsService.changeSoldOut(ids, soldOut));
    }
}
