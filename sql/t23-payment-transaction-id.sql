-- =============================================================
-- 三期微信支付 T23：支付流水表增加微信支付单号列
-- 依赖：ruoyi-takeout.sql 的 biz_payment_record（order_no 唯一约束）
-- 幂等性说明：可重复执行前请先确认列不存在（列已存在时本脚本会报 Duplicate column）
-- =============================================================
USE takeout;

ALTER TABLE biz_payment_record
    ADD COLUMN transaction_id varchar(64) DEFAULT NULL COMMENT '微信支付订单号（真实支付回调/查单写回；模拟支付为空）' AFTER mock_trade_no;
