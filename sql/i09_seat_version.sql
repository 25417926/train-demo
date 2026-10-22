-- ============================================================
-- I09 座位乐观锁版本列（可重复执行）
-- 作用：出票落库用 version CAS 更新 daily_train_seat.sell，
--       避免并发订单互相覆盖售卖位图。
-- ============================================================

set @i09_exists := (
    select count(*) from information_schema.columns
    where table_schema = database()
      and table_name = 'daily_train_seat'
      and column_name = 'version'
);
set @i09_ddl := if(@i09_exists = 0,
    'alter table `daily_train_seat` add column `version` int not null default 0 comment ''版本号''',
    'select 1');
prepare i09_stmt from @i09_ddl;
execute i09_stmt;
deallocate prepare i09_stmt;

-- 复核
select column_name, column_type, column_default
from information_schema.columns
where table_schema = database()
  and table_name = 'daily_train_seat'
  and column_name = 'version';
