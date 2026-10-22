-- ============================================================
-- I03 存量车票 order_id 回填 + ticket_status 对齐（一次性脚本）
-- 场景：train.ticket 与 train-business.confirm_order 分属两个库
--
-- 判定依据（不按 status 猜）：
--   1) 车票 -> 订单：member_id + train_date + train_code + start + end
--      + passenger_id + seat_type 完全相同；
--      订单必须是 S（出票成功）或 Y（已支付），且订单创建时间不晚于车票创建时间；
--      同一键有多个候选订单时，取创建时间最晚的那个（车票总是紧随其订单产生）
--   2) 订单 -> ticket_status：会员库存在该 order_id 的车票 => 'S'，否则 => 'N'
--
-- 候选订单状态为 S（出票成功待支付）和 Y（已支付）：
--   R/X 的票已在退款时删除，P/F/E 从未出票，均不参与绑定
--
-- 执行账号：需同时具备 train-business 与 train 两库权限（如 DBA/迁移账号）
-- 幂等：仅处理 order_id is null 的车票；ticket_status 每次按车票事实重算
--
-- 本地两账号权限不足（train-business 无权读 train.ticket，train-member 无权读
-- train-business.confirm_order），2026-10-22 实际执行分两步：
--   步骤 A（train-member）：按订单明细逐张核对后绑定未命中车票
--     update train.ticket set order_id = 2064091034354323456
--      where id = 2064091034660507648 and order_id is null;
--   步骤 B（train-business）：按 A 步得到的 22 个 order_id 重算 ticket_status
--     update confirm_order set ticket_status =
--       case when id in (<A 步 select distinct order_id>) then 'S' else 'N' end;
-- ============================================================
set names utf8mb4;

-- ---------- 1. 回填前核对 ----------
select 'ticket_total' as item, count(*) as cnt from `train`.`ticket`
union all select 'ticket_unmatched', count(*) from `train`.`ticket` where order_id is null
union all select 'order_S', count(*) from `train-business`.`confirm_order` where status = 'S'
union all select 'order_Y', count(*) from `train-business`.`confirm_order` where status = 'Y';

-- ---------- 2. 车票 -> 订单 ----------
update `train`.`ticket` t
join (
  select tk.id as ticket_id,
         (select o.id
            from `train-business`.`confirm_order` o
            join json_table(o.tickets, '$[*]' columns (
                   passenger_id bigint path '$.passengerId',
                   seat_type char(1) path '$.seatTypeCode')) jt
           where o.member_id = tk.member_id
             and o.`date` = tk.train_date
             and o.train_code = tk.train_code
             and o.`start` = tk.start_station
             and o.`end` = tk.end_station
             and jt.passenger_id = tk.passenger_id
             and jt.seat_type = tk.seat_type
             and o.status in ('S', 'Y')
             and o.create_time <= tk.create_time
           order by o.create_time desc, o.id desc
           limit 1) as order_id
  from `train`.`ticket` tk
  where tk.order_id is null
) m on m.ticket_id = t.id
set t.order_id = m.order_id,
    t.update_time = now(3)
where t.order_id is null
  and m.order_id is not null;

-- ---------- 3. 订单 ticket_status 按车票是否存在重算 ----------
update `train-business`.`confirm_order` o
left join `train`.`ticket` t on t.order_id = o.id
set o.ticket_status = case when t.id is null then 'N' else 'S' end,
    o.update_time = now(3);

-- ---------- 4. 结果与异常清单 ----------
select 'ticket_bound' as item, count(*) as cnt from `train`.`ticket` where order_id is not null
union all select 'ticket_unmatched', count(*) from `train`.`ticket` where order_id is null
union all select 'order_ticket_S', count(*) from `train-business`.`confirm_order` where ticket_status = 'S'
union all select 'order_ticket_N', count(*) from `train-business`.`confirm_order` where ticket_status = 'N';

-- 未命中的存量车票（异常清单）
select t.id as ticket_id, t.member_id, t.passenger_id, t.train_date, t.train_code,
       t.start_station, t.end_station, t.seat_type, t.create_time, 'NO_MATCHED_ORDER' as reason
from `train`.`ticket` t
where t.order_id is null
order by t.create_time;

-- 票在但订单状态不是 S/Y 的异常（应为 0）
select o.id as order_id, o.status, o.ticket_status, t.id as ticket_id
from `train-business`.`confirm_order` o
join `train`.`ticket` t on t.order_id = o.id
where o.status not in ('S', 'Y')
order by o.id;
