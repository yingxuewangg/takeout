-- ============================================================
-- 阿婆干饭社 —— 定时任务配置 SQL（T5：超时关单兜底）
-- 说明：
--   1) 全新环境：随 docker-compose 初始化自动执行（05 号脚本）
--   2) 已建库环境：手动执行一次即可
--   3) 任务类：com.ruoyi.merchant.task.TakeoutTask#closeTimeoutOrders
--      （RocketMQ 延迟消息为主，本任务低频扫描兜底防消息丢失，条件更新幂等）
-- ============================================================

SET NAMES utf8mb4;

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('阿婆干饭社-超时关单兜底', 'DEFAULT', 'takeoutTask.closeTimeoutOrders', '0 * * * * ?', '3', '1', '0', 'admin', sysdate(), '待支付订单超时自动关闭（RocketMQ 延迟消息的兜底，条件更新幂等）');

-- 退款 24 小时未审核自动驳回（T7，附录 B 决策 14）
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('阿婆干饭社-退款超时自动驳回', 'DEFAULT', 'takeoutTask.rejectTimeoutRefunds', '0 0 * * * ?', '3', '1', '0', 'admin', sysdate(), '退款单超过 24 小时未审核由系统自动驳回（效果与人工驳回一致，条件更新幂等）');

-- 每日库存初始化（T12，每天 00:00）
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('阿婆干饭社-每日库存初始化', 'DEFAULT', 'takeoutTask.initDailyStock', '0 0 0 * * ?', '3', '1', '0', 'admin', sysdate(), '每天 00:00 按限量配置初始化当天库存（唯一索引冲突即忽略，重复调度幂等；下单/查询另有惰性初始化兜底）');

