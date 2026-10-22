-- ============================================================
-- I06 在途 INIT 补偿（切换旧受理接口前执行一次）
-- 背景：旧链路靠 RocketMQ order_confirm 排队，切换后队列不再消费，
--       停留在 INIT 的订单会永久卡单，必须先补偿为失败，避免“无单可查”。
-- 特性：可重复执行；只处理创建超过 5 分钟的 INIT，避免误伤刚受理的订单。
-- 执行：mysql -utrain-business -p train-business < sql/i06_init_compensation.sql
-- ============================================================
set names utf8mb4;

-- ---------- 1. 预览：待补偿订单 ----------
select id, member_id, `date`, train_code, `start`, `end`, request_id, create_time
from confirm_order
where `status` = 'I'
  and create_time < now(3) - interval 5 minute
order by create_time;

-- ---------- 2. 标记为失败（带关闭原因，便于对账与人工复核） ----------
update confirm_order
set `status` = 'F',
    ticket_status = 'F',
    close_reason = 'INIT_COMPENSATE',
    update_time = now(3),
    version = version + 1
where `status` = 'I'
  and create_time < now(3) - interval 5 minute;

-- ---------- 3. 同步受理请求表：已受理但未完成的请求一并置失败 ----------
update purchase_request pr
join confirm_order co on co.id = pr.order_id
set pr.`status` = 'F',
    pr.update_time = now(3)
where co.close_reason = 'INIT_COMPENSATE'
  and pr.`status` in ('A', 'P');

-- ---------- 4. 复核结果 ----------
select `status`, ticket_status, count(*) as cnt
from confirm_order
where close_reason = 'INIT_COMPENSATE'
group by `status`, ticket_status;
