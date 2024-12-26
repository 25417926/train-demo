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