-- ============================================================
-- 阿婆干饭社 —— 二期 AI 智能问答模块建表 SQL（RAG）
-- 数据库：MySQL 8.x，库名 takeout
-- 执行顺序：在 ruoyi-takeout.sql 之后执行（本文件为 06 号初始化脚本）
-- 说明：
--   1) 向量数据存 Redis（RedisVectorStore / RediSearch），不在 MySQL；本文件只存知识库元数据与会话消息
--   2) 图片/文档字段同样只存"名称+相对路径"，不存域名（与一期文件存储约定一致）
--   3) Embedding 维度 = 1024（通义 text-embedding-v3 默认维度），换模型需重建 Redis 索引（见配置注释）
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1、AI 知识库文档表
-- ----------------------------
drop table if exists biz_ai_knowledge;
create table biz_ai_knowledge (
  id                bigint          not null auto_increment    comment '文档ID',
  file_name         varchar(255)    not null                   comment '原始文件名',
  file_path         varchar(500)    not null                   comment '文件相对路径（只存相对路径，不存域名）',
  file_type         varchar(20)     default ''                 comment '文件类型（txt/md/pdf/docx）',
  file_size         bigint          default 0                  comment '文件大小（字节）',
  chunk_count       int             default 0                  comment '切片数量（解析成功后写入）',
  parse_status      tinyint         not null default 0         comment '解析状态（0待解析 1解析中 2解析成功 3解析失败）',
  fail_reason       varchar(500)    default ''                 comment '失败原因（解析失败时写入）',
  vector_index_name varchar(100)    default ''                 comment '向量索引标识（RedisVectorStore 索引名，便于按文档清理）',
  create_by         varchar(64)     default ''                 comment '上传者',
  create_time       datetime        default null               comment '上传时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  key idx_biz_ai_knowledge_status (parse_status),
  key idx_biz_ai_knowledge_create (create_time)
) engine=innodb auto_increment=1 comment='AI 知识库文档表（向量存 Redis，本表只存元数据）';

-- ----------------------------
-- 2、AI 会话表
-- ----------------------------
drop table if exists biz_ai_chat_session;
create table biz_ai_chat_session (
  id                bigint          not null auto_increment    comment '会话ID',
  session_no        varchar(64)     not null                   comment '会话标识（对外展示/引用用，唯一）',
  member_id         bigint          not null                   comment '所属用户ID',
  title             varchar(100)    default ''                 comment '会话标题（取首次提问前 20 字）',
  message_count     int             default 0                  comment '消息条数',
  last_active_time  datetime        default null               comment '最后活跃时间',
  create_time       datetime        default null               comment '创建时间',
  update_time       datetime        default null               comment '更新时间',
  primary key (id),
  unique key uk_biz_ai_session_no (session_no),
  key idx_biz_ai_session_member (member_id, last_active_time)
) engine=innodb auto_increment=1 comment='AI 会话表';

-- ----------------------------
-- 3、AI 会话消息表
--    命中知识片段以 JSON 存快照，便于验证 RAG 是否生效、便于排障回溯
-- ----------------------------
drop table if exists biz_ai_chat_message;
create table biz_ai_chat_message (
  id                bigint          not null auto_increment    comment '消息ID',
  session_id        bigint          not null                   comment '会话ID',
  role              varchar(20)     not null                   comment '角色（user 用户 / assistant 助手）',
  content           text                                       comment '消息内容',
  references_json   json            default null               comment '命中知识片段引用（JSON数组：[{text,score,source}]，仅 assistant 消息有）',
  cost_ms           bigint          default 0                  comment '本次回答耗时（毫秒）',
  create_time       datetime        default null               comment '创建时间',
  primary key (id),
  key idx_biz_ai_message_session (session_id, create_time)
) engine=innodb auto_increment=1 comment='AI 会话消息表';

-- ============================================================
-- 管理端菜单：AI 知识库
-- ============================================================
insert into sys_menu values('5080', 'AI 知识库', '5000', '7', 'ai', 'merchant/ai/index', '', '', 1, 0, 'C', '0', '0', 'merchant:ai:knowledge:list', 'education', 'admin', sysdate(), '', null, 'AI 知识库管理菜单');
insert into sys_menu values('5081', '知识库查询', '5080', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:ai:knowledge:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5082', '知识库上传', '5080', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:ai:knowledge:upload', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5083', '知识库删除', '5080', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:ai:knowledge:remove', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('5084', '知识库重解析', '5080', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'merchant:ai:knowledge:reparse', '#', 'admin', sysdate(), '', null, '');
