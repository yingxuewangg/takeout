-- T18 骑手取餐与配送状态增强：订单配送环节时间字段
-- 方案 A：不改主状态机（status 0-6 定义不变），仅在现有条件更新流转内写入时间。
-- 可空 datetime：历史单据不回填（NULL 表示该环节未发生/早于本功能）。
--   ready_time      出餐时间（管理端出餐 2->3 时写入）
--   picked_up_time  骑手取餐时间（外卖 3->4「骑手已取餐」时写入）
--   delivered_time  送达时间（外卖确认送达 4->5 时写入）
USE takeout;

ALTER TABLE biz_order
    ADD COLUMN ready_time      datetime NULL DEFAULT NULL COMMENT '出餐时间（主状态2->3时写入，条件更新幂等不覆盖）' AFTER pay_time,
    ADD COLUMN picked_up_time  datetime NULL DEFAULT NULL COMMENT '骑手取餐时间（外卖主状态3->4时写入）' AFTER ready_time,
    ADD COLUMN delivered_time  datetime NULL DEFAULT NULL COMMENT '送达时间（外卖主状态4->5时写入）' AFTER picked_up_time;
