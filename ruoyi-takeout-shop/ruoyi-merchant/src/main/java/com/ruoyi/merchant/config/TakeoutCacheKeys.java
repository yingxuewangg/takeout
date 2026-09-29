package com.ruoyi.merchant.config;

/**
 * 阿婆干饭社 Redis 缓存 key 常量（统一 takeout: 前缀）。
 *
 * 缓存一致性约定（方案 3.5）：Cache-Aside，写操作统一"先更新数据库，再删除缓存"，
 * 只删不更新（含批量操作），由下次读请求回填；订单/购物车等交易数据不缓存。
 * 读缓存的回填逻辑在 T3 小程序端接口实现，管理端写操作只负责删除。
 *
 * @author 阿婆干饭社
 */
public class TakeoutCacheKeys
{
    /** 店铺信息缓存（含配送费/起送价/营业状态），shop_id 固定 1 */
    public static final String SHOP_INFO = "takeout:shop:1";

    /** 分类 + 在售菜品列表缓存（含菜品级售罄标记） */
    public static final String GOODS_LIST = "takeout:goods:list";

    /** 小程序登录态 token 前缀（T3 使用）：takeout:token:{token} */
    public static final String MEMBER_TOKEN_KEY = "takeout:token:";

    /** 下单防重锁前缀（T5 使用）：takeout:order:lock:{memberId}，setnx 5 秒过期 */
    public static final String ORDER_LOCK_KEY = "takeout:order:lock:";

    /** 退款防重锁前缀（T7 使用）：takeout:refund:lock:{memberId}:{orderId}，setnx 5 秒过期 */
    public static final String REFUND_LOCK_KEY = "takeout:refund:lock:";
}
