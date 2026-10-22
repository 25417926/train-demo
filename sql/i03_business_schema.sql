-- ============================================================
-- I03 数据模型变更（业务库 train-business）
-- 目标：
--   1) 新建 6 张表：purchase_request / purchase_qualification / order_outbox
--      / inventory_hold / inventory_release_record / waitlist_application
--   2) 变更现有表：confirm_order / payment / refund / change_record
-- 特性：可重复执行（建表 if not exists；加列/加索引先查 information_schema）
-- 执行：mysql -utrain-business -p train-business < sql/i03_business_schema.sql
-- ============================================================
set names utf8mb4;

-- ---------- 1. 新增表 ----------

create table if not exists `purchase_request` (
  `id` bigint not null comment 'id',
  `request_id` varchar(64) not null comment '客户端幂等键',
  `member_id` bigint not null comment '会员id',
  `payload_hash` char(64) not null comment '请求体摘要',
  `status` char(1) not null comment 'A受理 P处理中 S成功 F失败',
  `order_id` bigint null comment '订单id',
  `qualification_id` bigint null comment '首个资格id（多乘客时仅作索引）',
  `create_time` datetime(3) not null,
  `update_time` datetime(3) not null,
  primary key (`id`),
  unique key `uk_request_id` (`request_id`),
  key `idx_member_created` (`member_id`, `create_time`)
) engine=innodb default charset=utf8mb4 comment='购票请求幂等表';

create table if not exists `purchase_qualification` (
  `id` bigint not null comment 'id',
  `request_id` varchar(64) not null comment '请求幂等键',
  `member_id` bigint not null,
  `passenger_id` bigint not null,
  `date` date not null,
  `train_code` varchar(20) not null,
  `start` varchar(20) not null,
  `end` varchar(20) not null,
  `seat_type` char(1) not null,
  `order_id` bigint null comment '绑定订单id',
  `status` char(1) not null comment 'H占用 C已消费 R已释放 T已转移 E过期 X取消',
  `active_key` varchar(160) null comment '有效占位唯一键；释放后置 NULL',
  `expire_at` datetime(3) null comment '占位过期时间',
  `version` int not null default 0,
  `create_time` datetime(3) not null,
  `update_time` datetime(3) not null,
  primary key (`id`),
  unique key `uk_request_passenger` (`request_id`, `passenger_id`),
  unique key `uk_active` (`active_key`),
  key `idx_order` (`order_id`),
  key `idx_member_status` (`member_id`, `status`)
) engine=innodb default charset=utf8mb4 comment='购票资格（用户场次占位记录）';

create table if not exists `order_outbox` (
  `id` bigint not null comment 'id',
  `event_id` varchar(64) not null comment '事件唯一id',
  `aggregate_type` varchar(32) not null comment '聚合类型 ORDER/QUALIFICATION/WAITLIST',
  `aggregate_id` bigint not null comment '聚合id',
  `event_type` varchar(48) not null comment 'ORDER_CONFIRM/TICKET_CREATE/TICKET_DELETE/WAITLIST_MATCH',
  `payload` json not null comment '事件体',
  `status` char(1) not null comment 'N新建 S已投递 F失败 D死信',
  `retry_count` int not null default 0,
  `next_retry_at` datetime(3) not null,
  `last_error` varchar(500) null,
  `create_time` datetime(3) not null,
  `update_time` datetime(3) not null,
  primary key (`id`),
  unique key `uk_event_id` (`event_id`),
  key `idx_status_next` (`status`, `next_retry_at`, `id`)
) engine=innodb default charset=utf8mb4 comment='Outbox事件表';

create table if not exists `inventory_release_record` (
  `id` bigint not null comment 'id',
  `idempotent_key` varchar(96) not null comment '释放幂等键',
  `qualification_id` bigint not null,
  `order_id` bigint not null,
  `release_type` char(1) not null comment 'C取消 T超时 R退款 G改签 M补位失败',
  `seat_count` int not null comment '释放座位数',
  `status` char(1) not null comment 'S成功 F失败',
  `create_time` datetime(3) not null,
  primary key (`id`),
  unique key `uk_idempotent_key` (`idempotent_key`),
  key `idx_order` (`order_id`),
  key `idx_qualification` (`qualification_id`)
) engine=innodb default charset=utf8mb4 comment='库存释放流水';

create table if not exists `waitlist_application` (
  `id` bigint not null comment 'id',
  `request_id` varchar(64) not null comment '申请幂等键',
  `member_id` bigint not null,
  `passenger_id` bigint not null,
  `passenger_name` varchar(20) not null,
  `date` date not null,
  `train_code` varchar(20) not null,
  `start` varchar(20) not null,
  `end` varchar(20) not null,
  `seat_type` char(1) not null,
  `seq_no` bigint not null comment '同票档排队序号',
  `status` char(1) not null comment 'W等待 M匹配中 D已匹配 E过期 C取消 F失败',
  `active_key` varchar(160) null comment '有效申请唯一键；结束置 NULL',
  `matched_order_id` bigint null,
  `expire_at` datetime(3) null comment '补位订单支付截止',
  `create_time` datetime(3) not null,
  `update_time` datetime(3) not null,
  primary key (`id`),
  unique key `uk_request_id` (`request_id`),
  unique key `uk_active` (`active_key`),
  key `idx_queue` (`date`, `train_code`, `start`, `end`, `seat_type`, `status`, `seq_no`),
  key `idx_member_status` (`member_id`, `status`)
) engine=innodb default charset=utf8mb4 comment='候补申请';

create table if not exists `inventory_hold` (
  `id` bigint not null comment 'id',
  `hold_token` varchar(64) not null comment 'Redis 预扣凭据',
  `request_id` varchar(64) not null comment '请求幂等键',
  `order_id` bigint not null comment '订单id',
  `member_id` bigint not null,
  `passenger_id` bigint not null,
  `date` date not null,
  `train_code` varchar(20) not null,
  `seat_id` bigint not null comment 'daily_train_seat.id',
  `seat_type` char(1) not null,
  `start_index` int not null,
  `end_index` int not null,
  `status` char(1) not null comment 'H持有 R已释放',
  `create_time` datetime(3) not null,
  `release_time` datetime(3) null,
  primary key (`id`),
  unique key `uk_hold_seat` (`hold_token`, `seat_id`),
  key `idx_order` (`order_id`),
  key `idx_train_date` (`date`, `train_code`),
  key `idx_status` (`status`)
) engine=innodb default charset=utf8mb4 comment='库存预扣/占座账本';

-- ---------- 2. 现有表变更辅助过程（可重复执行） ----------
drop procedure if exists `i03_add_column`;
drop procedure if exists `i03_add_index`;
drop procedure if exists `i03_drop_index`;

delimiter $$
create procedure `i03_add_column`(in p_table varchar(64), in p_column varchar(64), in p_ddl varchar(500))
begin
  if not exists (select 1 from information_schema.columns
                 where table_schema = database() and table_name = p_table and column_name = p_column) then
    set @i03_sql = concat('alter table `', p_table, '` add column `', p_column, '` ', p_ddl);
    prepare i03_stmt from @i03_sql;
    execute i03_stmt;
    deallocate prepare i03_stmt;
  end if;
end$$

create procedure `i03_add_index`(in p_table varchar(64), in p_index varchar(64), in p_ddl varchar(500))
begin
  if not exists (select 1 from information_schema.statistics
                 where table_schema = database() and table_name = p_table and index_name = p_index) then
    set @i03_sql = concat('alter table `', p_table, '` add ', p_ddl);
    prepare i03_stmt from @i03_sql;
    execute i03_stmt;
    deallocate prepare i03_stmt;
  end if;
end$$

create procedure `i03_drop_index`(in p_table varchar(64), in p_index varchar(64))
begin
  if exists (select 1 from information_schema.statistics
             where table_schema = database() and table_name = p_table and index_name = p_index) then
    set @i03_sql = concat('alter table `', p_table, '` drop index `', p_index, '`');
    prepare i03_stmt from @i03_sql;
    execute i03_stmt;
    deallocate prepare i03_stmt;
  end if;
end$$
delimiter ;

-- confirm_order
call i03_add_column('confirm_order', 'request_id', 'varchar(64) null comment ''幂等请求id''');
call i03_add_column('confirm_order', 'qualification_id', 'bigint null comment ''主资格id''');
call i03_add_column('confirm_order', 'expire_at', 'datetime(3) null comment ''超时时间''');
call i03_add_column('confirm_order', 'close_reason', 'varchar(32) null comment ''关闭原因''');
call i03_add_column('confirm_order', 'ticket_status', 'char(1) not null default ''N'' comment ''N未出票 I出票中 S已出票 F失败''');
call i03_add_column('confirm_order', 'version', 'int not null default 0 comment ''版本号''');
call i03_add_index('confirm_order', 'uk_request_id', 'unique key `uk_request_id` (`request_id`)');
call i03_add_index('confirm_order', 'idx_status_expire', 'key `idx_status_expire` (`status`, `expire_at`)');
call i03_add_index('confirm_order', 'idx_member_status', 'key `idx_member_status` (`member_id`, `status`, `id`)');

-- payment：同一订单只允许一条"当前"支付记录；失败尝试复用该行，成功后置 S
call i03_add_column('payment', 'fail_reason', 'varchar(500) null');
call i03_add_index('payment', 'uk_order_id', 'unique key `uk_order_id` (`order_id`)');

-- refund：一次订单可能有"失败→重试成功"多条流水，因此只用普通索引，不加唯一键
call i03_add_column('refund', 'idempotent_key', 'varchar(96) null comment ''退款幂等键''');
call i03_add_column('refund', 'fail_reason', 'varchar(500) null');
call i03_add_index('refund', 'uk_idempotent_key', 'unique key `uk_idempotent_key` (`idempotent_key`)');
call i03_drop_index('refund', 'uk_order_id');
call i03_add_index('refund', 'idx_order_id', 'key `idx_order_id` (`order_id`)');
-- 说明：refund_status 现为 char(1)，若需“退款中”请用单字符编码（如 R），不要直接写入 REFUNDING。

-- change_record
call i03_add_column('change_record', 'request_id', 'varchar(64) null comment ''幂等请求id''');
call i03_add_column('change_record', 'fail_reason', 'varchar(500) null');
call i03_add_index('change_record', 'uk_request_id', 'unique key `uk_request_id` (`request_id`)');

drop procedure if exists `i03_add_column`;
drop procedure if exists `i03_add_index`;
drop procedure if exists `i03_drop_index`;

-- ---------- 3. 存量数据对齐 ----------
-- ticket_status 无法仅凭 confirm_order.status 推导：
--   S=成功、Y=已支付 都可能已有车票；R/X 订单退款后车票已被删除；P/F 从未出票。
-- 因此存量 ticket_status 必须按 train.ticket 中是否存在对应 order_id 的车票来判定，
-- 放在 sql/i03_backfill_ticket_order_id.sql 中执行（需跨库账号），此处不做批量假设更新。

-- ---------- 4. 结果核对 ----------
select table_name, table_rows
from information_schema.tables
where table_schema = database()
  and table_name in ('purchase_request','purchase_qualification','order_outbox',
                     'inventory_hold','inventory_release_record','waitlist_application');

select table_name, column_name
from information_schema.columns
where table_schema = database()
  and ((table_name = 'confirm_order' and column_name in ('request_id','qualification_id','expire_at','close_reason','ticket_status','version'))
    or (table_name = 'refund' and column_name in ('idempotent_key','fail_reason'))
    or (table_name = 'change_record' and column_name = 'request_id'))
order by table_name, ordinal_position;

select table_name, index_name, group_concat(column_name order by seq_in_index) as cols
from information_schema.statistics
where table_schema = database()
  and index_name in ('uk_request_id','idx_status_expire','idx_member_status','uk_order_id',
                     'uk_idempotent_key','uk_event_id','uk_active','uk_request_passenger','uk_hold_seat')
group by table_name, index_name
order by table_name, index_name;
