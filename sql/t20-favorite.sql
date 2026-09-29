-- T20 用户体验增强包 · 子项④收藏菜品
-- 用户收藏表：member_id + goods_id 唯一索引防重复收藏；不做逻辑删除（取消收藏物理删除，列表无历史需求）
USE takeout;

CREATE TABLE IF NOT EXISTS biz_goods_favorite (
    id          bigint   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    member_id   bigint   NOT NULL COMMENT '用户ID（biz_member.id）',
    goods_id    bigint   NOT NULL COMMENT '菜品ID（biz_goods.id）',
    create_time datetime NOT NULL COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_goods (member_id, goods_id),
    KEY idx_goods_id (goods_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户收藏菜品（T20，单店不做多门店隔离）';
