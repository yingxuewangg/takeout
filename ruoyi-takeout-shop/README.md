# 阿婆干饭社 · 后端服务

基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（MIT License）二次开发的单店点餐系统后端，Java 17 + Spring Boot + MyBatis-Plus + MySQL 8 + Redis + RocketMQ。

## 模块说明

| 模块 | 说明 |
|---|---|
| `ruoyi-merchant` | 商家管理端业务：订单状态机流转、退款审核、数据看板聚合、库存、WebSocket 新订单推送 |
| `ruoyi-user-api` | 小程序端 `/api` 接口服务：独立登录态（Redis token）、菜单/购物车/下单/支付/留言/收藏/AI 问答 |
| `ruoyi-admin` | 启动模块（端口 4121），聚合各模块与配置 |
| `ruoyi-framework` / `ruoyi-system` / `ruoyi-common` / `ruoyi-generator` / `ruoyi-quartz` | 若依框架原模块 |
| `sql/` | 建表脚本与各迭代增量 SQL |
| `docker-compose.yml` | 一键启动 MySQL 8 + Redis + RocketMQ（NameServer + Broker） |

## 核心设计

- **订单主状态（0–6）与退款状态（refund_status）彻底分离**，所有流转为条件更新 + 影响行数校验的幂等操作；
- **模拟支付策略模式**：`PaymentService` 接口 + `MockPaymentService`，金额以服务端库内为准，可无缝扩展真实微信支付；
- **RocketMQ 延迟消息**实现 10 分钟超时关单，定时任务兜底，`rocketmq.enabled=false` 时自动降级；
- 菜品/店铺数据 **Cache-Aside 缓存**（只删不更新）；下单/退款 **setnx 防重锁**；每日限量库存**条件扣减防超卖**；
- AI 问答基于 **Spring AI + RAG**（Redis 向量库），API Key 走环境变量 `DASHSCOPE_API_KEY`，不提交 Git。

## 启动

```bash
docker compose up -d          # 中间件
mvn -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar   # 端口 4121
```

数据库初始化见根目录 README「快速开始」。
