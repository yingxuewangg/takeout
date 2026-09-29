-- T19 数据看板增强：接单时间字段（支持"平均接单耗时"指标）
-- 与 T18 时间字段同模式：可空 datetime、条件更新流转内写入（1->2 接单时写 accept_time）、
-- 历史单据不回填（NULL 表示未记录/早于本功能）。
USE takeout;

ALTER TABLE biz_order
    ADD COLUMN accept_time datetime NULL DEFAULT NULL COMMENT '接单时间（主状态1->2时写入，条件更新幂等不覆盖）' AFTER pay_time;
