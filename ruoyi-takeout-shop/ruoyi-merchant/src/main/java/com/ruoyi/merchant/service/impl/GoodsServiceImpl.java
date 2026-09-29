package com.ruoyi.merchant.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsFlavor;
import com.ruoyi.merchant.domain.BizGoodsSpec;
import com.ruoyi.merchant.mapper.BizGoodsFlavorMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizGoodsSpecMapper;
import com.ruoyi.merchant.service.GoodsService;

/**
 * 菜品 服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class GoodsServiceImpl implements GoodsService
{
    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizGoodsSpecMapper goodsSpecMapper;

    @Autowired
    private BizGoodsFlavorMapper goodsFlavorMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public IPage<BizGoods> selectGoodsPage(IPage<BizGoods> page, BizGoods query)
    {
        LambdaQueryWrapper<BizGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(query.getName()), BizGoods::getName, query.getName())
               .eq(query.getCategoryId() != null, BizGoods::getCategoryId, query.getCategoryId())
               .eq(StringUtils.isNotEmpty(query.getStatus()), BizGoods::getStatus, query.getStatus())
               .eq(StringUtils.isNotEmpty(query.getSoldOut()), BizGoods::getSoldOut, query.getSoldOut())
               .orderByAsc(BizGoods::getSort)
               .orderByDesc(BizGoods::getId);
        return goodsMapper.selectPage((Page<BizGoods>) page, wrapper);
    }

    @Override
    public BizGoods selectGoodsById(Long id)
    {
        BizGoods goods = goodsMapper.selectById(id);
        if (goods == null)
        {
            throw new ServiceException("菜品不存在");
        }
        goods.setSpecs(goodsSpecMapper.selectList(new LambdaQueryWrapper<BizGoodsSpec>()
                .eq(BizGoodsSpec::getGoodsId, id).orderByAsc(BizGoodsSpec::getSort)));
        goods.setFlavors(goodsFlavorMapper.selectList(new LambdaQueryWrapper<BizGoodsFlavor>()
                .eq(BizGoodsFlavor::getGoodsId, id).orderByAsc(BizGoodsFlavor::getSort)));
        return goods;
    }

    @Override
    @Transactional
    public int insertGoods(BizGoods goods)
    {
        goods.setId(null);
        goods.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isEmpty(goods.getStatus()))
        {
            goods.setStatus("1");
        }
        if (StringUtils.isEmpty(goods.getSoldOut()))
        {
            goods.setSoldOut("0");
        }
        if (StringUtils.isEmpty(goods.getAutoSoldOut()))
        {
            goods.setAutoSoldOut("0");
        }
        if (StringUtils.isEmpty(goods.getDailyLimitEnabled()))
        {
            goods.setDailyLimitEnabled("0");
        }
        if (goods.getDailyLimitQty() == null)
        {
            goods.setDailyLimitQty(0);
        }
        if (goods.getSales() == null)
        {
            goods.setSales(0);
        }
        int rows = goodsMapper.insert(goods);
        saveSpecsAndFlavors(goods);
        if (rows > 0)
        {
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    @Transactional
    public int updateGoods(BizGoods goods)
    {
        if (goods.getId() == null)
        {
            throw new ServiceException("菜品ID不能为空");
        }
        goods.setUpdateTime(DateUtils.getNowDate());
        int rows = goodsMapper.updateById(goods);
        if (rows > 0)
        {
            // 规格、口味先删后插（级联更新）
            saveSpecsAndFlavors(goods);
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteGoodsByIds(Long[] ids)
    {
        List<Long> idList = Arrays.asList(ids);
        int rows = goodsMapper.deleteBatchIds(idList);
        if (rows > 0)
        {
            // 级联删除规格、口味
            goodsSpecMapper.delete(new LambdaQueryWrapper<BizGoodsSpec>().in(BizGoodsSpec::getGoodsId, idList));
            goodsFlavorMapper.delete(new LambdaQueryWrapper<BizGoodsFlavor>().in(BizGoodsFlavor::getGoodsId, idList));
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    public int changeGoodsStatus(Long id, String status)
    {
        BizGoods goods = new BizGoods();
        goods.setId(id);
        goods.setStatus(status);
        goods.setUpdateTime(DateUtils.getNowDate());
        int rows = goodsMapper.updateById(goods);
        if (rows > 0)
        {
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    public int changeSoldOut(Long[] ids, String soldOut)
    {
        List<BizGoods> list = Arrays.stream(ids).map(id -> {
            BizGoods goods = new BizGoods();
            goods.setId(id);
            goods.setSoldOut(soldOut);
            goods.setUpdateTime(DateUtils.getNowDate());
            return goods;
        }).toList();
        int rows = 0;
        for (BizGoods goods : list)
        {
            rows += goodsMapper.updateById(goods);
        }
        if (rows > 0)
        {
            // 批量操作同样只删缓存，由下次读请求回填
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

	/**
	 * 保存规格与口味。
	 * 规格：按 ID upsert（保留已有规格 ID，购物车行与订单快照对规格的引用不因编辑而失效——
	 *      T11 规格级售罄需按 specId 实时比对，ID 必须稳定），仅删除本次表单已移除的规格；
	 * 口味：先删后插（口味快照按名称存储，不依赖口味 ID）。
	 */
	private void saveSpecsAndFlavors(BizGoods goods)
	{
		List<BizGoodsSpec> specs = goods.getSpecs();
		List<Long> keepSpecIds = new java.util.ArrayList<>();
		if (specs != null)
		{
			for (BizGoodsSpec spec : specs)
			{
				// 规格级售罄随编辑弹窗表单透传（T11）；未传时默认未售罄
				if (StringUtils.isEmpty(spec.getSoldOut()))
				{
					spec.setSoldOut("0");
				}
				// 自动售罄标记由库存逻辑维护（T12），表单不参与；新增行默认 0
				if (spec.getId() == null && StringUtils.isEmpty(spec.getAutoSoldOut()))
				{
					spec.setAutoSoldOut("0");
				}
				BizGoodsSpec existing = spec.getId() == null ? null : goodsSpecMapper.selectById(spec.getId());
				if (existing == null || !goods.getId().equals(existing.getGoodsId()))
				{
					// 新增行（或 ID 不属于本菜品）：插入
					spec.setId(null);
					spec.setGoodsId(goods.getId());
					goodsSpecMapper.insert(spec);
				}
				else
				{
					// 已有行：原地更新（ID 保持不变）
					spec.setGoodsId(goods.getId());
					goodsSpecMapper.updateById(spec);
				}
				keepSpecIds.add(spec.getId());
			}
		}
		// 删除本次表单已移除的规格（保留仍存在的 ID）
		LambdaQueryWrapper<BizGoodsSpec> delSpecWrapper = new LambdaQueryWrapper<BizGoodsSpec>()
				.eq(BizGoodsSpec::getGoodsId, goods.getId());
		if (!keepSpecIds.isEmpty())
		{
			delSpecWrapper.notIn(BizGoodsSpec::getId, keepSpecIds);
		}
		goodsSpecMapper.delete(delSpecWrapper);

		goodsFlavorMapper.delete(new LambdaQueryWrapper<BizGoodsFlavor>().eq(BizGoodsFlavor::getGoodsId, goods.getId()));
		List<BizGoodsFlavor> flavors = goods.getFlavors();
		if (flavors != null)
		{
			for (BizGoodsFlavor flavor : flavors)
			{
				flavor.setId(null);
				flavor.setGoodsId(goods.getId());
				goodsFlavorMapper.insert(flavor);
			}
		}
	}
}
