package com.ruoyi.userapi.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.vo.FavoriteItemVo;
import com.ruoyi.userapi.service.ApiFavoriteService;

/**
 * 小程序端收藏菜品接口（T20 子项④，走登录态；数据按 member 隔离）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/favorite")
public class ApiFavoriteController extends BaseController
{
    @Autowired
    private ApiFavoriteService favoriteService;

    /**
     * 收藏/取消收藏（切换），返回切换后状态
     */
    @PostMapping("/toggle/{goodsId}")
    public AjaxResult toggle(@PathVariable Long goodsId)
    {
        boolean favorited = favoriteService.toggle(ApiMemberContext.requireMemberId(), goodsId);
        Map<String, Object> data = new HashMap<>();
        data.put("favorited", favorited);
        return success(data);
    }

    /**
     * 是否已收藏（商品详情❤初始状态）
     */
    @GetMapping("/{goodsId}/status")
    public AjaxResult status(@PathVariable Long goodsId)
    {
        boolean favorited = favoriteService.isFavorited(ApiMemberContext.requireMemberId(), goodsId);
        Map<String, Object> data = new HashMap<>();
        data.put("favorited", favorited);
        return success(data);
    }

    /**
     * 收藏的菜品ID集合（首页菜单❤标记，一次取全）
     */
    @GetMapping("/ids")
    public AjaxResult ids()
    {
        List<Long> ids = favoriteService.favoritedGoodsIds(ApiMemberContext.requireMemberId());
        return success(ids);
    }

    /**
     * 收藏分页列表（下架/失效商品置灰展示）
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize)
    {
        Page<FavoriteItemVo> page = favoriteService.pageFavorites(ApiMemberContext.requireMemberId(), pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }
}
