package com.ruoyi.merchant.service;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.merchant.domain.BizGoods;

/**
 * 菜品 服务层（含规格/口味级联与菜品级售罄标记）
 *
 * @author 阿婆干饭社
 */
public interface GoodsService
{
    /**
     * 分页查询菜品列表（含分类名称过滤等）
     */
    IPage<BizGoods> selectGoodsPage(IPage<BizGoods> page, BizGoods query);

    /**
     * 查询菜品详情（级联带出规格、口味）
     */
    BizGoods selectGoodsById(Long id);

    /**
     * 新增菜品（级联保存规格、口味）
     *
     * @return 影响行数
     */
    int insertGoods(BizGoods goods);

    /**
     * 修改菜品（级联更新：规格、口味先删后插）
     *
     * @return 影响行数
     */
    int updateGoods(BizGoods goods);

    /**
     * 批量删除菜品（级联删除规格、口味）
     *
     * @return 影响行数
     */
    int deleteGoodsByIds(Long[] ids);

    /**
     * 修改上架状态（1在售 0下架）
     *
     * @return 影响行数
     */
    int changeGoodsStatus(Long id, String status);

    /**
     * 批量设置售罄标记（菜品级：1售罄 0取消售罄）
     *
     * @return 影响行数
     */
    int changeSoldOut(Long[] ids, String soldOut);
}
