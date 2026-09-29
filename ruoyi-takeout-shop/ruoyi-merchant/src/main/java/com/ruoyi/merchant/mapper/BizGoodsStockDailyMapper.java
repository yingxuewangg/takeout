package com.ruoyi.merchant.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;

/**
 * 每日库存 Mapper（T12）。
 * 扣减/释放用条件更新（影响行数校验）保证并发安全与幂等：
 *  - deduct：where sold_qty + N <= limit_qty，行数=0 即库存不足（防超卖）
 *  - release：where sold_qty >= N，行数=0 即无库存可还（防负数）
 *
 * @author 阿婆干饭社
 */
@Mapper
public interface BizGoodsStockDailyMapper extends BaseMapper<BizGoodsStockDaily>
{
    /**
     * 条件扣减库存（防超卖）：仅当剩余足够时才更新。
     * 注意：stock_date 为 DATE 列，入参可能是带时分秒的 Date（如下单日期），
     * 必须用 DATE() 归一化，否则 DATE 列与 DATETIME 值比较不相等（会静默不匹配）。
     * @return 影响行数；0 表示库存不足（或无当天库存行）
     */
    @Update("update biz_goods_stock_daily "
            + "set sold_qty = sold_qty + #{qty}, update_time = now() "
            + "where goods_id = #{goodsId} and spec_id = #{specId} and stock_date = date(#{stockDate}) "
            + "and sold_qty + #{qty} <= limit_qty")
    int deduct(@Param("goodsId") Long goodsId,
               @Param("specId") Long specId,
               @Param("stockDate") Date stockDate,
               @Param("qty") int qty);

    /**
     * 条件释放库存（防负数）：仅当已售足够时才回退。
     * 同样用 date() 归一化日期（释放传的是订单下单时间，含时分秒）。
     * @return 影响行数；0 表示该行不存在或已售为 0
     */
    @Update("update biz_goods_stock_daily "
            + "set sold_qty = sold_qty - #{qty}, update_time = now() "
            + "where goods_id = #{goodsId} and spec_id = #{specId} and stock_date = date(#{stockDate}) "
            + "and sold_qty >= #{qty}")
    int release(@Param("goodsId") Long goodsId,
                @Param("specId") Long specId,
                @Param("stockDate") Date stockDate,
                @Param("qty") int qty);
}
