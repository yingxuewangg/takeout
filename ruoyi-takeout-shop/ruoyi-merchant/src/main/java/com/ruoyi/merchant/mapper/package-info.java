/**
 * 共用业务表的数据访问层（MyBatis-Plus BaseMapper）。
 *
 * 表归属约定：
 * - 两端共用的业务表（biz_shop_info/biz_category/biz_goods/biz_goods_spec/biz_goods_flavor/
 *   biz_order/biz_order_item/biz_order_refund/biz_payment_record/biz_goods_comment）
 *   实体与 Mapper 定义在本包，ruoyi-user-api 依赖本模块复用；
 * - 用户专属表（biz_member/biz_address/biz_cart）的实体与 Mapper 放在
 *   com.ruoyi.userapi.mapper。
 */
package com.ruoyi.merchant.mapper;
