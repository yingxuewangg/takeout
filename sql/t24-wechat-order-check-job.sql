-- =============================================================
-- 三期微信支付 T24：微信支付查单兜底定时任务
-- 依赖：ruoyi-takeout-job.sql（sys_job 表 + 现有阿婆干饭社任务行）
-- 幂等性说明：查单兜底落账走条件更新（where status=0）+ 流水唯一约束，重复调度无副作用；
--             已存在同 invoke_target 任务时请勿重复插入
-- =============================================================
USE takeout;

insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('阿婆干饭社-微信支付查单兜底', 'DEFAULT', 'takeoutTask.checkWechatOrders', '0 * * * * ?', '3', '1', '0', 'admin', sysdate(), '三期T24：对已下单未回调的微信支付单每分钟查单，微信侧已支付则补落账（幂等）');
