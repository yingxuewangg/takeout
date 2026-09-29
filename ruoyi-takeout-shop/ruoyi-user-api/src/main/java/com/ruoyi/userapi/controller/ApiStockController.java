package com.ruoyi.userapi.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.service.StockService;
import com.ruoyi.userapi.domain.vo.GoodsStockVo;

/**
 * 小程序端库存查询（T12）。
 *
 * 说明（方案要求 8）：库存不进菜单缓存 takeout:goods:list，剩余库存由本接口单独返回，
 * 直接查库不缓存，保证实时性。游客可访问（与菜单浏览一致）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/goods/stock")
public class ApiStockController
{
    @Autowired
    private StockService stockService;

    @Autowired
    private BizGoodsMapper goodsMapper;

    /** 单个菜品当天库存（详情页用） */
    @GetMapping("/{goodsId}")
    public AjaxResult getStock(@PathVariable Long goodsId)
    {
        return AjaxResult.success(buildVo(goodsId, today()));
    }

    /** 批量查询多个菜品当天库存（列表页用，逗号分隔 goodsIds） */
    @GetMapping("/batch")
    public AjaxResult getStockBatch(@RequestParam String goodsIds)
    {
        List<Long> ids = new ArrayList<>();
        for (String s : goodsIds.split(","))
        {
            String t = s.trim();
            if (!t.isEmpty())
            {
                try
                {
                    ids.add(Long.valueOf(t));
                }
                catch (NumberFormatException ignored)
                {
                    // 非法 id 忽略
                }
            }
        }
        if (ids.isEmpty())
        {
            return AjaxResult.success(List.of());
        }
        Date today = today();
        Map<Long, Map<Long, BizGoodsStockDaily>> batch = stockService.getGoodsStockMapBatch(ids, today);
        List<GoodsStockVo> result = new ArrayList<>();
        for (Long id : ids)
        {
            GoodsStockVo vo = buildVo(id, today, batch.get(id));
            if (vo != null)
            {
                result.add(vo);
            }
        }
        return AjaxResult.success(result);
    }

    private GoodsStockVo buildVo(Long goodsId, Date today)
    {
        return buildVo(goodsId, today, stockService.getGoodsStockMap(goodsId, today));
    }

    /** 组装库存 VO；未启用每日限量返回 null（调用方过滤） */
    private GoodsStockVo buildVo(Long goodsId, Date today, Map<Long, BizGoodsStockDaily> stockMap)
    {
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !"1".equals(goods.getDailyLimitEnabled()))
        {
            return null;
        }
        GoodsStockVo vo = new GoodsStockVo();
        vo.setGoodsId(goodsId);
        vo.setDailyLimitEnabled(true);
        BizGoodsStockDaily goodsLevel = stockMap == null ? null
                : stockMap.get(BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL);
        if (goodsLevel != null)
        {
            vo.setRemainQty(goodsLevel.remainQty());
            vo.setLimitQty(goodsLevel.getLimitQty());
        }
        List<GoodsStockVo.SpecStockVo> specs = new ArrayList<>();
        if (stockMap != null)
        {
            for (Map.Entry<Long, BizGoodsStockDaily> e : stockMap.entrySet())
            {
                if (e.getKey() == BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL)
                {
                    continue;
                }
                GoodsStockVo.SpecStockVo sv = new GoodsStockVo.SpecStockVo();
                sv.setSpecId(e.getKey());
                sv.setRemainQty(e.getValue().remainQty());
                sv.setLimitQty(e.getValue().getLimitQty());
                specs.add(sv);
            }
        }
        vo.setSpecs(specs);
        return vo;
    }

    private Date today()
    {
        return DateUtils.parseDate(DateUtils.getDate());
    }
}
