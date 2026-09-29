package com.ruoyi.userapi.service;

import java.util.List;
import com.ruoyi.userapi.domain.vo.GoodsDetailVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo.MenuItemVo;

/**
 * 小程序端菜单服务接口
 *
 * @author 阿婆干饭社
 */
public interface ApiMenuService
{
    /**
     * 分类 + 在售菜品列表（Cache-Aside：读 takeout:goods:list，miss 查库回填，24h TTL 兜底；
     * 管理端增删改/上下架/售罄/批量操作后已删除该缓存）
     */
    List<MenuCategoryVo> getMenuList();

    /**
     * 按名称关键字搜索在售菜品（从缓存列表内存过滤，不打库）
     */
    List<MenuItemVo> searchGoods(String keyword);

    /**
     * 商品详情（查库，含规格/口味；售罄菜品可查看详情，图片 URL 后端拼好）
     */
    GoodsDetailVo getGoodsDetail(Long id);
}
