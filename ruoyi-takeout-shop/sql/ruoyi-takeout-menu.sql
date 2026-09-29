-- 初始化会话字符集（容器内 mysql 客户端无 LANG 时默认 latin1，会导致中文双重编码）
SET NAMES utf8mb4;
-- ============================================================
-- 阿婆干饭社 —— 管理端菜单 SQL（T2）
-- 说明：
--   1) 全新环境：随 docker-compose 初始化自动执行（04 号脚本）
--   2) 已建库环境：手动执行一次即可  mysql -uroot -proot takeout < ruoyi-takeout-menu.sql
--   3) 菜单挂在外卖管理目录下；admin 为超级管理员自动拥有全部权限，
--      普通角色请在 系统管理-角色管理 中自行勾选授权
-- ============================================================

-- 一级目录：外卖管理
insert into sys_menu values('5000', '外卖管理', '0', '5', 'takeout', null, '', '', 1, 0, 'M', '0', '0', '', 'shopping', 'admin', sysdate(), '', null, '阿婆干饭社管理目录');

-- 菜单：店铺信息
insert into sys_menu values('5010', '店铺信息', '5000', '1', 'shop', 'merchant/shop/index', '', '', 1, 0, 'C', '0', '0', 'merchant:shop:query', 'guide', 'admin', sysdate(), '', null, '店铺信息菜单');
-- 店铺信息按钮
insert into sys_menu values('5011', '店铺修改', '5010', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:shop:edit', '#', 'admin', sysdate(), '', null, '');

-- 菜单：分类管理
insert into sys_menu values('5020', '分类管理', '5000', '2', 'category', 'merchant/category/index', '', '', 1, 0, 'C', '0', '0', 'goods:category:list', 'tree', 'admin', sysdate(), '', null, '菜品分类菜单');
-- 分类管理按钮
insert into sys_menu values('5021', '分类查询', '5020', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:category:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5022', '分类新增', '5020', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:category:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5023', '分类修改', '5020', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:category:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5024', '分类删除', '5020', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:category:remove', '#', 'admin', sysdate(), '', null, '');

-- 菜单：菜品管理
insert into sys_menu values('5030', '菜品管理', '5000', '3', 'goods', 'merchant/goods/index', '', '', 1, 0, 'C', '0', '0', 'goods:goods:list', 'documentation', 'admin', sysdate(), '', null, '菜品管理菜单');
-- 菜品管理按钮
insert into sys_menu values('5031', '菜品查询', '5030', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5032', '菜品新增', '5030', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:add', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5033', '菜品修改', '5030', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:edit', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5034', '菜品删除', '5030', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5035', '菜品上下架', '5030', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:shelf', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5036', '菜品售罄标记', '5030', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'goods:goods:soldOut', '#', 'admin', sysdate(), '', null, '');

-- 菜单：订单管理（T6）
insert into sys_menu values('5040', '订单管理', '5000', '4', 'order', 'merchant/order/index', '', '', 1, 0, 'C', '0', '0', 'merchant:order:list', 'list', 'admin', sysdate(), '', null, '订单管理菜单');
-- 订单管理按钮
insert into sys_menu values('5041', '订单查询', '5040', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:order:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5042', '订单接单', '5040', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:order:accept', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5043', '订单出餐', '5040', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:order:ready', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5044', '订单配送', '5040', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:order:deliver', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5045', '订单完成', '5040', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:order:complete', '#', 'admin', sysdate(), '', null, '');

-- 菜单：退款管理（T7）
insert into sys_menu values('5060', '退款管理', '5000', '5', 'refund', 'merchant/refund/index', '', '', 1, 0, 'C', '0', '0', 'merchant:refund:list', 'money', 'admin', sysdate(), '', null, '退款审核菜单');
-- 退款管理按钮
insert into sys_menu values('5061', '退款查询', '5060', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:refund:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5062', '退款通过', '5060', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:refund:approve', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5063', '退款驳回', '5060', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:refund:reject', '#', 'admin', sysdate(), '', null, '');

-- 菜单：留言管理（T8）
insert into sys_menu values('5070', '留言管理', '5000', '6', 'comment', 'merchant/comment/index', '', '', 1, 0, 'C', '0', '0', 'merchant:comment:list', 'message', 'admin', sysdate(), '', null, '留言管理菜单');
-- 留言管理按钮
insert into sys_menu values('5071', '留言查询', '5070', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:comment:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5072', '留言回复', '5070', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:comment:reply', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5073', '留言隐藏', '5070', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:comment:hide', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5074', '留言删除', '5070', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:comment:remove', '#', 'admin', sysdate(), '', null, '');

-- 菜单：今日库存（T12 每日限量 + 自动售罄）
insert into sys_menu values('5090', '今日库存', '5000', '8', 'stock', 'merchant/stock/index', '', '', 1, 0, 'C', '0', '0', 'merchant:stock:list', 'shopping', 'admin', sysdate(), '', null, '每日限量库存查看与补货');
-- 今日库存按钮
insert into sys_menu values('5091', '库存查询', '5090', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:stock:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5092', '库存重置', '5090', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:stock:reset', '#', 'admin', sysdate(), '', null, '');

-- 菜单：数据看板（T14 管理端统计图表）
insert into sys_menu values('5100', '数据看板', '5000', '9', 'dashboard', 'merchant/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'merchant:dashboard:view', 'chart', 'admin', sysdate(), '', null, '营业额/订单量/菜品销量统计看板');
-- 数据看板按钮
insert into sys_menu values('5101', '看板查看', '5100', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:dashboard:view', '#', 'admin', sysdate(), '', null, '');
