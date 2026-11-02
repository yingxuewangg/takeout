-- =============================================================
-- 三期微信支付 T25：退款单增加微信退款受理号列 + 退款查单兜底任务
-- 依赖：ruoyi-takeout.sql 的 biz_order_refund；ruoyi-takeout-job.sql 的 sys_job
-- 幂等性说明：可重复执行前请先确认列不存在（列已存在时 ALTER 会报 Duplicate column；
--             sys_job 按 job_name+invoke_target 唯一，重复执行前先人工确认）
-- =============================================================
USE takeout;

ALTER TABLE biz_order_refund
    ADD COLUMN refund_id varchar(64) DEFAULT NULL COMMENT '微信退款受理号（真实退款回调/查单兜底写回；模拟退款为空）' AFTER is_mock_refund;

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('阿婆干饭社-微信退款查单兜底', 'DEFAULT', 'takeoutTask.checkWechatRefunds', '0 * * * * ?', '3', '1', '0', 'admin', sysdate(),
        '三期T25：对已受理未回写受理号的微信退款单逐单查单，成功补写 refund_id，异常记日志人工处理（幂等）');
