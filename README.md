# train-demo

分布式铁路售票系统演示项目，基于 Spring Cloud Alibaba 微服务架构，覆盖会员、车次、余票、下单、支付、退款、退改签等完整链路。

## 模块结构

```
train-demo
├── common         基础公共模块（响应封装、异常、JWT、雪花算法、拦截器）
├── gateway        Spring Cloud Gateway 网关，Sa-Token 鉴权
├── auth (AUTH)    认证授权服务（用户/角色/权限/邮箱验证码）
├── train-member   会员服务（注册/登录/乘客/车票）
├── business       业务核心（车次/车厢/座位/余票/订单/支付/退款/退改签）
├── batch          Quartz 定时任务（每日车次/座位生成）
├── builder        基于 FreeMarker 的代码生成器
├── admin-ui       管理后台前端 (Vue 3 + Ant Design Vue)
├── vue-ui         会员端前端 (Vue 3 + Ant Design Vue)
└── sql            建表脚本
```

## 技术栈

### 后端
| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.6.13 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2021.0.5 / 2021.0.5.0 |
| 网关 | Spring Cloud Gateway | - |
| 认证 | Sa-Token / Hutool JWT | 1.39.0 / 5.8.27 |
| 服务治理 | Nacos Discovery / Config | - |
| 远程调用 | OpenFeign | - |
| 限流熔断 | Sentinel | - |
| ORM | MyBatis + MyBatis Generator | 2.2.0 |
| 分页 | PageHelper | 1.4.7 |
| 缓存 | Redis（Lettuce 连接池 + Redisson 分布式锁） | 3.2 |
| 消息 | RocketMQ（Spring Cloud Stream Binder，事务消息） | 4.9.4 / Binder 2021.0.5.0 |
| 可观测 | Micrometer 指标端点（受理时延 / Lua 耗时 / 消费堆积 / 库存偏差） | - |
| 规则引擎 | Drools | 8.44.0.Final |
| 验证码 | Kaptcha | - |
| API 文档 | Knife4j（OpenAPI3） | 4.4.0 |
| 工具 | Hutool / FastJSON / Lombok | - |
| Java | JDK | 1.8 |

### 前端
| 类别 | 技术 |
|------|------|
| 核心 | Vue 3 |
| UI | Ant Design Vue 3 |
| 表格 | @surely-vue/table |
| HTTP | Axios |
| 状态 | Vuex |
| 路由 | Vue Router |
| 构建 | Vue CLI |

## 端口与上下文

| 模块 | 端口 | context-path |
|------|------|--------------|
| gateway | 80 | / |
| auth | 81 | /auth |
| train-member | 8080 | /member |
| business | 8081 | /business |
| batch | 8082 | /batch |

## API 文档（Knife4j）

直连各服务：

- business：http://localhost:8081/business/doc.html
- train-member：http://localhost:8080/member/doc.html
- batch：http://localhost:8082/batch/doc.html
- auth：http://localhost:81/auth/doc.html

> 网关上挂了 Sa-Token 全路径鉴权，访问 doc.html 建议直连各微服务端口。

## 核心流程

### 余票查询
1. 用户输入出发站、到达站、日期
2. 关联 `daily_train` 与 `daily_train_ticket`，返回各席位余票

### 下单（防止超卖：幂等键 + Lua 预扣 + 事务消息）
1. 客户端调 `POST /business/web/purchase/apply`，同时带 `Idempotency-Key`（>=8 位）
2. 令牌大闸削峰：Redis 单次 `DECR` 快路径，只在缓存缺失时回源 `sk_token` 表补令牌
3. `reserve.lua` 一次原子完成：受理幂等判重 → 乘客资格占位 → 余票校验 → 座位池 `LPOP` 选座并逐段判冲突 → 扣余票 → 写 hold 凭据
4. 事务消息：半消息发出后执行本地事务（`purchase_qualification` / `purchase_request` / `confirm_order` / `order_outbox`），成功才 `COMMIT`
5. `order-confirm` 消费：出票落账（座位 sell、hold、余票合并写），再发出票事件由 `ticket-create` 消费，经 `MemberFeign` 写会员车票
6. 受理即进入 5 分钟关闭窗口（`order:close` ZSet），到期由关单调度兜底关单并归还库存

### 库存一致性
- 余票扣减走 `TicketDeltaLedger` 内存合并，每 100ms 批量写库，避免全车次共用同一行的锁竞争
- 库存对账每 60s 比对 Redis 与 MySQL：余票（ticket）、座位占用（seat）、账本（seat_ledger）
- 全库孤儿 key 清扫（SCAN）降为 10 分钟一次，分钟级只比对已知 key，避免扫描阻塞 Redis 单线程
- 对账发现 Redis 与库不一致时以 MySQL 为准回写，存在在途扣减的行跳过本轮

### 超时关单
- `OrderCloseScheduler` 每 5s 轮询 `order:close`（批量 100），每 300s 兜底扫描过期订单
- 关单先落账再释放：订单 CAS → 资格/hold 释放 → 账本回滚 → 释放流水 + `INVENTORY_RELEASED` outbox，事务提交后再执行 `release.lua`

### 支付（演示场景）
- `POST /business/web/payment/pay` → 直接落 payment 记录并置已支付，订单状态推进为 PAID

### 退款 / 退票
1. 校验订单为 PAID
2. 通过 MemberFeign 反查该订单关联车票（拿到 carriageIndex/row/col/seatType）
3. 反查 `daily_train_seat`，按区间二进制位反向 mask 回滚 sell
4. 调 `CUST_Daily_Train_TicketMapper.increaseCountBySell` 回滚余票区间计数
5. 调 MemberFeign 删除会员车票
6. payment 标记已退款，订单 → REFUNDED，落 refund 记录

### 退改签
- 改签 = 退原票（沿用 RefundService）+ 重新下单（沿用 Before_Confirm_OrderService.BeforeDoConfirm）
- 原订单状态推进为 CHANGED，新订单经 MQ 异步出票，落 change_record 记录
- 演示版固定手续费 5 元

## 性能实测（单机）

验收口径：**单机下单受理 ≥ 600 单/秒**（不含异步出票消费能力）。

压测方式：单台机器同时跑 business 服务、MySQL 8.0、Redis 3.2、RocketMQ 与压测驱动，走 `POST /business/web/purchase/apply`（每单 1 张票），每轮 15s，测前重置库存并清空消费堆积。

| 并发 | 时长 | 受理吞吐 | p50 | p95 | p99 | 拒绝 |
|------|------|----------|-----|-----|-----|------|
| 96 | 15s | **663.0 单/秒** | 119ms | 258ms | 601ms | 0 |
| 160 | 15s | **718.6 单/秒** | 182ms | 397ms | 1429ms | 0 |

一致性校验（10,814 单受理后）：

- `daily_train_ticket.ydz` 余票由 21500 → 10686，与受理单量精确对齐
- `inventoryDrift`（ticket / seat / seat_ledger）全部为 0
- `mqConsumeLag` 归零，`mqDlqTotal` 无新增

### 本轮优化项与效果

| 优化项 | 手段 | 效果 |
|--------|------|------|
| 候补退避推进 | `KEYS waitlist:retry:*` → 索引 Set `waitlist:retry:buckets` | 消除每秒 125~187ms 的 Redis 全库扫描 |
| 库存对账 | 全库 `SCAN` 由每分钟 3 次降为 10 分钟 1 次，分钟级只比对已知 key | 消除每次 10~16ms 的 SCAN 阻塞 |
| 令牌大闸 | 去 Redisson 锁与前置 GET，改单次 `DECR` 快路径，DB 抵扣 1/5 → 1/100 | 受理 p50 从 180ms 降至 ~120ms |
| Redis 连接池 | 维持 `max-active=32`（曾试 96，`reserve.lua` 均耗时 8.6ms → 20.8ms 反而更慢） | 避免连接数拉高后的排队劣化 |
| 热点余票行 | `TicketDeltaLedger` 内存聚合，100ms 批量落库 | 消除全车次共用同一行余票的锁竞争 |

> 瓶颈结论：Redis 3.2 单线程下，后台 `KEYS` / `SCAN` 全库扫描会把阻塞时延叠加到所有受理请求上；连接池、线程数等横向扩容手段无效，优先级应放在消除全库扫描。

### 可观测端点

`GET /business/internal/observability/metrics`（仅内部访问）返回：

- `purchaseAccept.latency`：受理时延（count / mean / p50 / p95 / p99 / max）
- `luaDurationMs.reserve`：`reserve.lua` 执行耗时
- `mqConsumeLag` / `mqDlqTotal`：各消费组堆积与死信量
- `inventoryDrift`：ticket / seat / seat_ledger 三类库存偏差

## 前端入口

### vue-ui（会员端，1001）
- 侧栏菜单新增"我的订单"
- `/my-order` 列出订单（订单号 / 日期 / 车次 / 出发到达 / 状态）
- 每行操作列按订单状态显示按钮：成功 → 支付；已支付 → 退票 / 改签
- 支付 / 退票 / 改签 各自一个弹窗收集参数后调用对应后端接口

### admin-ui（管理后台，1002）
- 仿 sk-token 风格新增三个只读列表页（admin-ui 通过 URL 直达）：
  - `/business/payment` 支付记录
  - `/business/refund` 退款记录
  - `/business/change-record` 退改签记录

### 后端为前端补的列表接口
- `GET /business/web/payment/Query-List`
- `GET /business/web/refund/Query-List`
- `GET /business/web/change/Query-List`

## 数据库

`sql/` 目录下：

- `member.sql` 会员/乘客/车票
- `business.sql` 车次相关全部表 + 支付/退款/退改签 3 张新表
- `batch.sql` Quartz 表
- `UserRole.sql` / `user.sql` 权限相关
- `seata.sql` 分布式事务 undo_log

## 启动顺序

1. 起 MySQL（建库 `train`、`train-business`、`train-batch` 并各执行对应 sql）
2. 起 Redis
3. 起 Nacos
4. 起 RocketMQ（business 异步出票需要）
5. 各微服务 IDE 启动 / `mvn -pl xxx -am spring-boot:run`
6. 前端 `cd vue-ui && yarn install && yarn serve`、`cd admin-ui && yarn install && yarn serve`

## 已完成的安全/规范改造

- /redis、/test、/getNacos 调试接口已注释下线
- 内部 Feign 路径迁移到 `/internal/feign/**`，网关层 SaTokenConfig 已配置拒绝外部访问
- 全模块接入 Knife4j（OpenAPI3）API 文档
- 业务闭环：补齐了支付、退款/退票、退改签三套链路（厚实现）
- 扩展了 ConfirmOrderStatusEnum：新增 PAID / REFUNDED / CHANGED 状态
- 前后端打通：vue-ui 新增"我的订单"页（含支付/退票/改签按钮），admin-ui 新增 payment/refund/change-record 三个后台列表页
- 下单入口统一为 `POST /business/web/purchase/apply`，强制 `Idempotency-Key`（>=8 位），重复键直接回放首次受理结果
- 防超卖下沉到 Redis：`reserve.lua` 单脚本完成幂等判重、资格占位、余票校验、座位选取与 hold 写凭据
- 余票扣减改内存聚合批量落库（`TicketDeltaLedger`），库存对账 + 超时关单调度兜底，`inventoryDrift` 归零
- 受理链路接入 Micrometer 指标，`/business/internal/observability/metrics` 可查询时延、消费堆积与库存偏差
- 性能验收：单机下单受理 663~718 单/秒（96 / 160 并发），达标 ≥600 单/秒

## 待改进

- FastJSON 1.2.70 存在已知 CVE，建议升级 FastJSON2 或 Jackson
- API 版本号（`/api/v1/`）尚未引入
- Sentinel 限流阈值需按接口/IP 维度细化
- 单元测试覆盖率仍然较低
- 真实第三方支付通道未对接，演示版直接置为支付成功
- DLQ 尚需人工重放，自动重放与告警待补
- member 出票链路仍有两次 Feign 调用可合并（`ticketService.issue` + `updateTicketStatus` 单次 CAS）
- 批量 SQL 待补：座位批量查询、hold 批量写入、资格批量 `INSERT IGNORE`
- Redis 为 3.2 单线程版本，后台全库扫描类操作需长期约束；建议升级 Redis 7 并启用 `unlink`/多线程 IO
