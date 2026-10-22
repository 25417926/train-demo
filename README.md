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
| 缓存 | Redis（Redisson 分布式锁） | - |
| 消息 | RocketMQ | 2.2.3 |
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

### 下单（防超卖）
1. 客户端调 `POST /business/web/confirm-order/do-storage`
2. `Before_Confirm_OrderService` 校验秒杀令牌（Redis）→ 落 `confirm_order`（INIT）→ 发 RocketMQ
3. `ConfirmOrderConsumer` 消费 → `Confirm_OrderService.DoStorage` 拿 Redisson 分布式锁
4. 选座（按区间二进制位运算判断 sell）→ `After_Confirm_OrderService` 更新 sell、扣余票、调 MemberFeign 写车票、订单 → SUCCESS

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

## 待改进

- FastJSON 1.2.70 存在已知 CVE，建议升级 FastJSON2 或 Jackson
- API 版本号（`/api/v1/`）尚未引入
- Sentinel 限流阈值需按接口/IP 维度细化
- 单元测试覆盖率仍然较低
- 真实第三方支付通道未对接，演示版直接置为支付成功
