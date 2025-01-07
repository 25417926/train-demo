create table member
(
    id     bigint      not null
        primary key,
    mobile varchar(11) null,
    constraint member_mobile
        unique (mobile)
);

create table passenger
(
    id          bigint      not null comment 'id'
        primary key,
    member_id   bigint      not null comment '会员id',
    name        varchar(20) not null comment '姓名',
    id_card     varchar(18) not null comment '身份证',
    type        char        not null comment '旅客类型(枚举)',
    create_time datetime(3) null comment '新增时间',
    update_time datetime(3) null comment '修改时间'
);

create index member_index_id
    on passenger (member_id);

drop table if exists `ticket`;
create table `ticket` (
                          `id` bigint not null comment 'id',
                          `member_id` bigint not null comment '会员id',
                          `passenger_id` bigint not null comment '乘客id',
                          `passenger_name` varchar(20) comment '乘客姓名',
                          `train_date` date not null comment '日期',
                          `train_code` varchar(20) not null comment '车次编号',
                          `carriage_index` int not null comment '箱序',
                          `seat_row` char(2) not null comment '排号|01, 02',
                          `seat_col` char(1) not null comment '列号|枚举[SeatColEnum]',
                          `start_station` varchar(20) not null comment '出发站',
                          `start_time` time not null comment '出发时间',
                          `end_station` varchar(20) not null comment '到达站',
                          `end_time` time not null comment '到站时间',
                          `seat_type` char(1) not null comment '座位类型|枚举[SeatTypeEnum]',
                          `create_time` datetime(3) comment '新增时间',
                          `update_time` datetime(3) comment '修改时间',
                          primary key (`id`),
                          index `member_id_index` (`member_id`)
) engine=innodb default charset=utf8mb4 comment='车票';
