-- =============================================================
-- T11 规格级售罄：biz_goods_spec 增加规格级售罄标记（增量脚本）
-- 执行方式（当前库手动执行，docker-compose 初始化走 03 号全量脚本已同步）：
--   docker exec -i takeout-mysql mysql --default-character-set=utf8mb4 -uroot -p密码 takeout < sql/ruoyi-takeout-spec-soldout.sql
-- 索引说明：常用查询为「按 goods_id 查规格列表」，已有 idx_biz_goods_spec_goods(goods_id)
--   覆盖；sold_out 为 0/1 低区分度列，单列索引无收益，故不新增索引。
-- =============================================================
SET NAMES utf8mb4;

ALTER TABLE biz_goods_spec
  ADD COLUMN sold_out char(1) NOT NULL DEFAULT '0'
  COMMENT '规格级售罄标记（0未售罄 1已售罄；菜品级售罄 biz_goods.sold_out 优先级更高）' AFTER sort;
