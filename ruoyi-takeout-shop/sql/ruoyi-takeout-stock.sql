-- =============================================================
-- T12 每日限量库存 + 自动售罄：增量脚本（当前库执行）
-- 执行方式：
--   docker exec -i takeout-mysql mysql --default-character-set=utf8mb4 -uroot -proot takeout < sql/ruoyi-takeout-stock.sql
-- 说明：全新环境由 03 号全量脚本 ruoyi-takeout.sql 建好，本脚本仅供已建库环境增量升级。
-- =============================================================
SET NAMES utf8mb4;

-- 1) 菜品表：自动售罄标记 + 每日限量配置
ALTER TABLE biz_goods
  ADD COLUMN auto_sold_out tinyint NOT NULL DEFAULT 0
      COMMENT '自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动 sold_out）' AFTER sold_out,
  ADD COLUMN daily_limit_enabled tinyint NOT NULL DEFAULT 0
      COMMENT '是否启用每日限量（T12：0否 1是）' AFTER auto_sold_out,
  ADD COLUMN daily_limit_qty int DEFAULT 0
      COMMENT '每日限量值（菜品级模板，每天 00:00 据此初始化当天库存；规格级以 biz_goods_spec.daily_limit_qty 优先）' AFTER daily_limit_enabled;

-- 2) 规格表：自动售罄标记 + 规格级限量值
ALTER TABLE biz_goods_spec
  ADD COLUMN auto_sold_out tinyint NOT NULL DEFAULT 0
      COMMENT '自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动 sold_out）' AFTER sold_out,
  ADD COLUMN daily_limit_qty int DEFAULT NULL
      COMMENT '规格级每日限量值（T12：留空则回落菜品级 biz_goods.daily_limit_qty）' AFTER auto_sold_out;

-- 3) 订单表：库存释放幂等键
ALTER TABLE biz_order
  ADD COLUMN stock_released tinyint NOT NULL DEFAULT 0
      COMMENT '库存是否已释放（T12 幂等键：0未释放 1已释放；超时关单/取消/退款成功任一入口释放，只释放一次）' AFTER timeout_close_time;

-- 4) 每日库存表
CREATE TABLE IF NOT EXISTS biz_goods_stock_daily (
  id                bigint          not null auto_increment    comment '库存行ID',
  goods_id          bigint          not null                   comment '菜品ID',
  spec_id           bigint          not null default 0         comment '规格ID（0=菜品级；菜品级与规格级库存互不干扰）',
  stock_date        date            not null                   comment '库存日期（当天）',
  limit_qty         int             not null default 0         comment '当日限量值（初始化时从配置模板快照）',
  sold_qty          int             not null default 0         comment '当日已售数量（下单扣减、释放回退）',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  unique key uk_stock_goods_spec_date (goods_id, spec_id, stock_date),
  key idx_stock_date (stock_date)
) engine=innodb auto_increment=1 comment='每日库存表（菜品级/规格级每日限量）';
