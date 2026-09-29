-- 初始化会话字符集（容器内 mysql 客户端无 LANG 时默认 latin1，会导致中文双重编码）
SET NAMES utf8mb4;
-- ============================================================
-- 阿婆干饭社 —— 业务建表 SQL（一期 MVP，13 张 biz_ 表）
-- 数据库：MySQL 8.x，库名 takeout（由 docker-compose 环境变量创建）
-- 执行顺序：先执行 ry_20260417.sql（若依系统表）+ quartz.sql（定时任务表），再执行本文件
-- 通用约定：
--   1) 业务表统一前缀 biz_，金额字段 decimal(10,2)，不做多门店（shop_id 固定写 1）
--   2) 图片字段只存"名称+相对路径"（如 /profile/upload/2026/09/25/{uuid}.png），不存域名/完整 URL；多图用 JSON 数组
--   3) 订单主状态与退款状态彻底分离（方案 3.4），三套状态字典如下，建表注释/用户端/管理端必须一致：
--      · 订单主状态 biz_order.status：0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消
--      · 订单退款状态 biz_order.refund_status：0无退款 1退款审核中(主状态冻结/流转暂停) 2已退款(订单终态)
--      · 退款单审核状态 biz_order_refund.audit_status：0待审核 1已通过 2已驳回
-- ============================================================

-- ----------------------------
-- 1、小程序用户表
-- ----------------------------
drop table if exists biz_member;
create table biz_member (
  id                bigint          not null auto_increment    comment '用户ID',
  openid            varchar(64)     not null                   comment '微信openid（小程序端唯一标识）',
  nickname          varchar(64)     default ''                 comment '昵称',
  avatar            varchar(255)    default ''                 comment '头像（只存相对路径）',
  phone             varchar(20)     default ''                 comment '手机号',
  last_login_time   datetime        default null               comment '最近登录时间',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  unique key uk_biz_member_openid (openid)
) engine=innodb auto_increment=1 comment='小程序用户表';

-- ----------------------------
-- 2、店铺信息表（单店，shop_id 固定 1）
-- ----------------------------
drop table if exists biz_shop_info;
create table biz_shop_info (
  shop_id               bigint          not null                   comment '店铺ID（固定写1，不做多门店）',
  shop_name             varchar(64)     default ''                 comment '店铺名称',
  address               varchar(255)    default ''                 comment '店铺位置（文字地址）',
  longitude             decimal(10,6)   default null               comment '经度',
  latitude              decimal(10,6)   default null               comment '纬度',
  business_hours        varchar(100)    default ''                 comment '营业时间（如 09:00-21:00）',
  business_status       tinyint         default 1                  comment '营业状态（1营业中 0已打烊；打烊后用户可浏览不可下单）',
  phone                 varchar(20)     default ''                 comment '商家电话（退款等场景展示给用户）',
  notice                varchar(500)    default ''                 comment '店铺公告',
  delivery_fee          decimal(10,2)   default 0.00               comment '外卖配送费（下单时快照到订单）',
  min_delivery_amount   decimal(10,2)   default 0.00               comment '外卖起送价（菜品合计未达不可下单）',
  create_by             varchar(64)     default ''                 comment '创建者',
  create_time           datetime        default null               comment '创建时间',
  update_by             varchar(64)     default ''                 comment '更新者',
  update_time           datetime        default null               comment '更新时间',
  primary key (shop_id)
) engine=innodb comment='店铺信息表（单店）';

-- ----------------------------
-- 3、用户地址簿表
-- ----------------------------
drop table if exists biz_address;
create table biz_address (
  id                bigint          not null auto_increment    comment '地址ID',
  member_id         bigint          not null                   comment '用户ID',
  contact_name      varchar(32)     default ''                 comment '联系人',
  contact_phone     varchar(20)     default ''                 comment '联系电话',
  province          varchar(32)     default ''                 comment '省',
  city              varchar(32)     default ''                 comment '市',
  district          varchar(32)     default ''                 comment '区',
  detail            varchar(255)    default ''                 comment '详细地址',
  is_default        tinyint         default 0                  comment '是否默认地址（0否 1是）',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_address_member (member_id)
) engine=innodb auto_increment=1 comment='用户地址簿表';

-- ----------------------------
-- 4、菜品分类表
-- ----------------------------
drop table if exists biz_category;
create table biz_category (
  id                bigint          not null auto_increment    comment '分类ID',
  name              varchar(32)     not null                   comment '分类名称',
  sort              int             default 0                  comment '显示顺序（越小越靠前）',
  status            tinyint         default 0                  comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime        default null               comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime        default null               comment '更新时间',
  primary key (id)
) engine=innodb auto_increment=1 comment='菜品分类表';

-- ----------------------------
-- 5、菜品表
-- ----------------------------
drop table if exists biz_goods;
create table biz_goods (
  id                bigint          not null auto_increment    comment '菜品ID',
  category_id       bigint          not null                   comment '分类ID',
  name              varchar(64)     not null                   comment '菜品名称',
  image             varchar(255)    default ''                 comment '菜品图片（只存相对路径，不存域名）',
  price             decimal(10,2)   not null default 0.00      comment '售价（基础价）',
  description       varchar(500)    default ''                 comment '菜品描述',
  status            tinyint         default 1                  comment '上架状态（1在售 0下架）',
  sold_out          tinyint         default 0                  comment '手动售罄标记（菜品级：0否 1是；优先级高于自动售罄；售罄置灰不可加购可看详情）',
  auto_sold_out     tinyint         not null default 0         comment '自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动 sold_out）',
  daily_limit_enabled tinyint       not null default 0         comment '是否启用每日限量（T12：0否 1是）',
  daily_limit_qty   int             default 0                  comment '每日限量值（菜品级模板，每天 00:00 据此初始化当天库存；规格级以 biz_goods_spec.daily_limit_qty 优先）',
  sales             int             default 0                  comment '销量（支付成功的数据库事务内同步累加）',
  sort              int             default 0                  comment '显示顺序（越小越靠前）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime        default null               comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_goods_category (category_id)
) engine=innodb auto_increment=1 comment='菜品表';

-- ----------------------------
-- 6、菜品规格表（含差价）
-- ----------------------------
drop table if exists biz_goods_spec;
create table biz_goods_spec (
  id                bigint          not null auto_increment    comment '规格ID',
  goods_id          bigint          not null                   comment '菜品ID',
  name              varchar(32)     default ''                 comment '规格名（如大份/小份）',
  price_delta       decimal(10,2)   default 0.00               comment '规格差价（在基础价上加减）',
  sort              int             default 0                  comment '显示顺序',
  sold_out          char(1)         not null default '0'       comment '规格级售罄标记（0未售罄 1已售罄；手动，优先级高于自动售罄）',
  auto_sold_out     tinyint         not null default 0         comment '自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动 sold_out）',
  daily_limit_qty   int             default null               comment '规格级每日限量值（T12：留空则回落菜品级 biz_goods.daily_limit_qty）',
  primary key (id),
  key idx_biz_goods_spec_goods (goods_id)
) engine=innodb auto_increment=1 comment='菜品规格表';

-- ----------------------------
-- 7、菜品口味选项表（可多组、单选/多选）
-- ----------------------------
drop table if exists biz_goods_flavor;
create table biz_goods_flavor (
  id                bigint          not null auto_increment    comment '口味组ID',
  goods_id          bigint          not null                   comment '菜品ID',
  name              varchar(32)     default ''                 comment '口味组名（如辣度/温度）',
  options           json            default null               comment '选项列表（JSON数组，如 ["微辣","中辣","特辣"]）',
  select_type       tinyint         default 0                  comment '选择方式（0单选 1多选）',
  sort              int             default 0                  comment '显示顺序',
  primary key (id),
  key idx_biz_goods_flavor_goods (goods_id)
) engine=innodb auto_increment=1 comment='菜品口味选项表';

-- ----------------------------
-- 8、购物车表（口味/规格存快照，不随菜品修改变化）
-- ----------------------------
drop table if exists biz_cart;
create table biz_cart (
  id                bigint          not null auto_increment    comment '购物车记录ID',
  member_id         bigint          not null                   comment '用户ID',
  goods_id          bigint          not null                   comment '菜品ID',
  spec_id           bigint          default null               comment '规格ID（无规格为null）',
  spec_name         varchar(32)     default ''                 comment '规格名快照',
  flavor_json       json            default null               comment '口味快照（JSON数组，如 [{"name":"辣度","values":["微辣"]}]）',
  quantity          int             default 1                  comment '数量',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_cart_member (member_id)
) engine=innodb auto_increment=1 comment='购物车表';

-- ----------------------------
-- 9、订单主表
--    主状态 status 与退款状态 refund_status 彻底分离（核心设计）：
--    · 申请退款：主状态不变，refund_status -> 1（流转暂停）
--    · 驳回/超时驳回：refund_status -> 0，主状态不变，流转恢复，可再次申请
--    · 退款通过（模拟）：refund_status -> 2，订单终态，禁止一切流转
-- ----------------------------
drop table if exists biz_order;
create table biz_order (
  id                bigint          not null auto_increment    comment '订单ID',
  order_no          varchar(32)     not null                   comment '订单号（唯一）',
  member_id         bigint          not null                   comment '下单用户ID',
  delivery_type     tinyint         not null default 1         comment '履约方式（1堂食 2外卖配送）',
  table_no          varchar(20)     default ''                 comment '桌号（堂食必绑；扫码scene自动带入，同桌多单各自独立）',
  contact_name      varchar(32)     default ''                 comment '联系人（堂食=预留电话人/外卖=收货人）',
  contact_phone     varchar(20)     default ''                 comment '联系电话',
  address_snapshot  json            default null               comment '收货地址快照（JSON：省市区+详址；不随后续地址修改变化，堂食为null）',
  goods_amount      decimal(10,2)   not null default 0.00      comment '菜品合计',
  delivery_fee      decimal(10,2)   not null default 0.00      comment '配送费快照（堂食为0；外卖下单时从店铺信息快照）',
  pay_amount        decimal(10,2)   not null default 0.00      comment '实付金额（=菜品合计+配送费快照；退款金额以此为准）',
  status            tinyint         not null default 0         comment '订单主状态（仅业务流转：0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消）',
  refund_status     tinyint         not null default 0         comment '退款状态（与主状态分离：0无退款 1退款审核中(主状态冻结/流转暂停) 2已退款(终态)）',
  remark            varchar(200)    default ''                 comment '订单备注',
  pay_type          tinyint         default null               comment '支付方式（1微信支付-模拟）',
  pay_status        tinyint         default 0                  comment '支付状态（0未支付 1已支付）',
  pay_time          datetime        default null               comment '支付时间',
  timeout_close_time datetime       default null               comment '超时自动关闭时间（下单时=创建时间+10分钟）',
  stock_released    tinyint         not null default 0         comment '库存是否已释放（T12 幂等键：0未释放 1已释放；超时关单/取消/退款成功任一入口释放，只释放一次）',
  create_time       datetime        default null               comment '下单时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  unique key uk_biz_order_no (order_no),
  key idx_biz_order_member (member_id),
  key idx_biz_order_status_create (status, create_time)
) engine=innodb auto_increment=1 comment='订单主表';

-- ----------------------------
-- 10、订单明细表（菜品快照）
-- ----------------------------
drop table if exists biz_order_item;
create table biz_order_item (
  id                bigint          not null auto_increment    comment '明细ID',
  order_id          bigint          not null                   comment '订单ID',
  goods_id          bigint          not null                   comment '菜品ID',
  goods_name        varchar(64)     default ''                 comment '菜品名称快照',
  goods_image       varchar(255)    default ''                 comment '菜品图片快照（只存相对路径）',
  spec_flavor_json  json            default null               comment '规格口味快照（JSON，如 {"spec":{"name":"大份","priceDelta":3},"flavors":[{"name":"辣度","values":["微辣"]}]}）',
  unit_price        decimal(10,2)   not null default 0.00      comment '单价快照（含规格差价）',
  quantity          int             not null default 1         comment '数量',
  subtotal          decimal(10,2)   not null default 0.00      comment '小计（单价x数量）',
  primary key (id),
  key idx_biz_order_item_order (order_id)
) engine=innodb auto_increment=1 comment='订单明细表';

-- ----------------------------
-- 11、模拟支付流水表（订单号唯一约束用于支付幂等）
-- ----------------------------
drop table if exists biz_payment_record;
create table biz_payment_record (
  id                bigint          not null auto_increment    comment '流水ID',
  order_no          varchar(32)     not null                   comment '订单号（唯一约束：重复支付插入冲突即幂等拦截）',
  member_id         bigint          not null                   comment '支付用户ID',
  amount            decimal(10,2)   not null                   comment '支付金额（以库内订单实付金额为准，不信任前端）',
  pay_type          tinyint         default 1                  comment '支付方式（1微信支付-模拟）',
  pay_status        tinyint         default 1                  comment '支付状态（1成功）',
  is_mock           tinyint         default 1                  comment '模拟支付标记（1模拟；接真实支付后为0）',
  mock_trade_no     varchar(64)     default ''                 comment '模拟流水号',
  pay_time          datetime        default null               comment '支付时间',
  create_time       datetime        default null               comment '创建时间',
  primary key (id),
  unique key uk_biz_payment_order_no (order_no)
) engine=innodb auto_increment=1 comment='模拟支付流水表';

-- ----------------------------
-- 12、退款单表（驳回后可再次申请，每次申请新增一条记录，历史保留）
--     用户自助申请范围：订单主状态 1-3（待接单/已接单制作中/已出餐待取餐）且 refund_status=0；
--     配送中（4）不可自助申请（联系商家协商），未支付(0)/已完成(5)/已取消(6)不可申请
-- ----------------------------
drop table if exists biz_order_refund;
create table biz_order_refund (
  id                bigint          not null auto_increment    comment '退款单ID',
  order_no          varchar(32)     not null                   comment '订单号',
  member_id         bigint          not null                   comment '申请用户ID',
  refund_amount     decimal(10,2)   not null                   comment '退款金额（=订单实付金额，含配送费快照，仅整单退款）',
  reason            varchar(200)    not null                   comment '退款原因（必填）',
  evidence_images   json            default null               comment '图片凭证（JSON数组存相对路径，经 /api/file/upload 上传）',
  audit_status      tinyint         not null default 0         comment '审核状态（0待审核 1已通过(模拟退款成功) 2已驳回/超时自动驳回）',
  reject_reason     varchar(200)    default ''                 comment '驳回理由（用户可见）',
  is_auto_reject    tinyint         default 0                  comment '是否系统超时自动驳回（0否 1是；24小时未审核自动驳回）',
  is_mock_refund    tinyint         default 1                  comment '模拟退款标记（1模拟；接真实支付后为0）',
  refund_time       datetime        default null               comment '退款成功时间（审核通过时写入）',
  create_time       datetime        default null               comment '申请时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_refund_order_no (order_no),
  key idx_biz_refund_audit_create (audit_status, create_time)
) engine=innodb auto_increment=1 comment='退款单表';

-- ----------------------------
-- 13、商品留言表（登录即可留言，不强制已购；order_id 可空，关联时校验资格）
-- ----------------------------
drop table if exists biz_goods_comment;
create table biz_goods_comment (
  id                bigint          not null auto_increment    comment '留言ID',
  member_id         bigint          not null                   comment '留言用户ID',
  goods_id          bigint          not null                   comment '菜品ID',
  order_id          bigint          default null               comment '关联订单ID（可空；关联时校验：本人订单、主状态>=1；主状态5且refund_status!=2可关联，0/6或已退款不可关联）',
  content           varchar(500)    default ''                 comment '留言内容',
  images            json            default null               comment '留言图片（JSON数组存相对路径）',
  score             tinyint         default 5                  comment '评分（1-5）',
  reply             varchar(500)    default ''                 comment '商家回复',
  reply_time        datetime        default null               comment '回复时间',
  status            tinyint         default 0                  comment '状态（0正常 1隐藏/删除）',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_comment_goods (goods_id),
  key idx_biz_comment_member (member_id)
) engine=innodb auto_increment=1 comment='商品留言表';

-- ----------------------------
-- 14、每日库存表（T12 每日限量 + 自动售罄）
-- 说明：
--   1) spec_id 用 0 表示"菜品级"（不用 NULL：MySQL 唯一索引对 NULL 不去重，菜品级行会重复）；
--   2) 每天 00:00 定时任务按 biz_goods/biz_goods_spec 的 daily_limit_qty 初始化当天行；
--      下单/查询时若当天行不存在则惰性初始化兜底（依赖唯一索引，冲突即忽略）；
--   3) 扣减用条件更新（where sold_qty + N <= limit_qty）防超卖；库存不缓存，直接查库。
-- ----------------------------
drop table if exists biz_goods_stock_daily;
create table biz_goods_stock_daily (
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

-- ============================================================
-- 初始化数据
-- ============================================================

-- 店铺信息（shop_id 固定 1；电话/地址为示例数据，T2 管理端上线后可直接修改）
insert into biz_shop_info (shop_id, shop_name, address, longitude, latitude, business_hours, business_status, phone, notice, delivery_fee, min_delivery_amount, create_by, create_time)
values (1, '阿婆干饭社', '广东省广州市天河区幸福路 88 号（示例地址，请按实际修改）', 113.361991, 23.124674, '09:00-21:00', 1, '020-12345678', '欢迎光临阿婆干饭社，阿婆牌爱心盖饭今日有售～', 5.00, 20.00, 'admin', sysdate());

-- 演示分类（T2 菜单管理上线后可维护）
insert into biz_category (name, sort, status, create_by, create_time) values
('阿婆招牌', 1, 0, 'admin', sysdate()),
('清爽小食', 2, 0, 'admin', sysdate());

-- 演示菜品（含规格/口味；图片为本地上传文件，若 uploadPath 下不存在该文件请在管理端重新上传）
insert into biz_goods (category_id, name, image, price, description, status, sold_out, sales, sort, create_by, create_time)
values (1, '阿婆牌爱心盖饭', '/profile/upload/2026/09/25/06a6c62b596c42a585ad8a0456e99912.png', 18.00, '阿婆的招牌，爱心满满', 1, 0, 0, 1, 'admin', sysdate());
insert into biz_goods_spec (goods_id, name, price_delta, sort)
select id, '大份', 3.00, 1 from biz_goods where name='阿婆牌爱心盖饭';
insert into biz_goods_spec (goods_id, name, price_delta, sort)
select id, '小份', -2.00, 2 from biz_goods where name='阿婆牌爱心盖饭';
insert into biz_goods_flavor (goods_id, name, options, select_type, sort)
select id, '辣度', '["微辣", "中辣", "特辣"]', '0', 1 from biz_goods where name='阿婆牌爱心盖饭';
insert into biz_goods_flavor (goods_id, name, options, select_type, sort)
select id, '加料', '["煎蛋", "午餐肉", "溏心蛋"]', '1', 2 from biz_goods where name='阿婆牌爱心盖饭';
