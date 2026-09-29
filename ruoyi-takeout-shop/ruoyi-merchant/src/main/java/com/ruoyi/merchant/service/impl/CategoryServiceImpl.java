package com.ruoyi.merchant.service.impl;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizCategory;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.mapper.BizCategoryMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.service.CategoryService;

/**
 * 菜品分类 服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class CategoryServiceImpl implements CategoryService
{
    @Autowired
    private BizCategoryMapper categoryMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<BizCategory> selectCategoryList(BizCategory query)
    {
        LambdaQueryWrapper<BizCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(query.getName()), BizCategory::getName, query.getName())
               .eq(StringUtils.isNotEmpty(query.getStatus()), BizCategory::getStatus, query.getStatus())
               .orderByAsc(BizCategory::getSort);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public BizCategory selectCategoryById(Long id)
    {
        return categoryMapper.selectById(id);
    }

    @Override
    public int insertCategory(BizCategory category)
    {
        checkNameUnique(category);
        category.setCreateTime(DateUtils.getNowDate());
        int rows = categoryMapper.insert(category);
        if (rows > 0)
        {
            // 分类影响小程序端菜单列表，删除缓存（只删不更新）
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    public int updateCategory(BizCategory category)
    {
        checkNameUnique(category);
        category.setUpdateTime(DateUtils.getNowDate());
        int rows = categoryMapper.updateById(category);
        if (rows > 0)
        {
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    @Override
    public int deleteCategoryByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            Long count = goodsMapper.selectCount(new LambdaQueryWrapper<BizGoods>().eq(BizGoods::getCategoryId, id));
            if (count > 0)
            {
                BizCategory category = categoryMapper.selectById(id);
                throw new ServiceException(String.format("分类【%s】下存在菜品，不允许删除", category == null ? id : category.getName()));
            }
        }
        int rows = categoryMapper.deleteBatchIds(Arrays.asList(ids));
        if (rows > 0)
        {
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        return rows;
    }

    /** 同名分类查重（排除自身） */
    private void checkNameUnique(BizCategory category)
    {
        LambdaQueryWrapper<BizCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCategory::getName, category.getName())
               .ne(category.getId() != null, BizCategory::getId, category.getId());
        if (categoryMapper.selectCount(wrapper) > 0)
        {
            throw new ServiceException(String.format("分类名称【%s】已存在", category.getName()));
        }
    }
}
