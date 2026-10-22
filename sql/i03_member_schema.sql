-- ============================================================
-- I03 数据模型变更（会员库 train）
-- 变更：ticket 增加 order_id，支持“按订单精确退票/出票幂等”
-- 特性：可重复执行
-- 执行：mysql -utrain-member -p train < sql/i03_member_schema.sql
-- ============================================================
set names utf8mb4;

drop procedure if exists `i03_add_column`;
drop procedure if exists `i03_add_index`;

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
delimiter ;

call i03_add_column('ticket', 'order_id', 'bigint null comment ''业务订单id''');
call i03_add_index('ticket', 'uk_order_passenger', 'unique key `uk_order_passenger` (`order_id`, `passenger_id`)');
call i03_add_index('ticket', 'idx_member_date', 'key `idx_member_date` (`member_id`, `train_date`)');

drop procedure if exists `i03_add_column`;
drop procedure if exists `i03_add_index`;

-- 结果核对
select column_name, column_type, is_nullable
from information_schema.columns
where table_schema = database() and table_name = 'ticket' and column_name = 'order_id';

select index_name, group_concat(column_name order by seq_in_index) as cols
from information_schema.statistics
where table_schema = database() and table_name = 'ticket'
group by index_name;
