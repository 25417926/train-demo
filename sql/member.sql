create table member
(
    id     bigint      not null
        primary key,
    mobile varchar(11) null,
    constraint member_mobile
        unique (mobile)
);
