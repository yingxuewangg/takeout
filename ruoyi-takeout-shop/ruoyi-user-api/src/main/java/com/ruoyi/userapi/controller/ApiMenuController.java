package com.ruoyi.userapi.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.domain.vo.GoodsDetailVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo.MenuItemVo;
import com.ruoyi.userapi.service.ApiMenuService;

/**
 * 小程序端菜单接口（游客可浏览；售罄菜品列表置灰不可加购、可查看详情）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api")
public class ApiMenuController extends BaseController
{
    @Autowired
    private ApiMenuService apiMenuService;

    /**
     * 分类 + 在售菜品列表（走 takeout:goods:list 缓存）
     */
    @GetMapping("/menu/list")
    public AjaxResult menuList()
    {
        List<MenuCategoryVo> list = apiMenuService.getMenuList();
        return success(list);
    }

    /**
     * 按名称/描述关键字搜索在售菜品（缓存内过滤）
     */
    @GetMapping("/goods/search")
    public AjaxResult search(@RequestParam String keyword)
    {
        List<MenuItemVo> list = apiMenuService.searchGoods(keyword);
        return success(list);
    }

    /**
     * 商品详情（含规格/口味；售罄可查看详情）
     */
    @GetMapping("/goods/detail/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        GoodsDetailVo vo = apiMenuService.getGoodsDetail(id);
        return success(vo);
    }
}
