package com.guo.business.waitlist;

/**
 * 候补 Redis key 统一定义（I15）。
 *
 * <p>票档 key 为 {@code date:trainCode:start:end:seatType}：
 * 主队列 {@code waitlist:{key}}（ZSet，score=seq_no）、
 * 取号器 {@code waitlist:seq:{key}}、等待计数 {@code waitlist:count:{key}}；
 * I16 追加匹配合并锁 {@code waitlist:match:key:{key}}、退避队列
 * {@code waitlist:retry:{key}}、失败计数 {@code waitlist:fail:{applicationId}}
 * 与事件幂等标记 {@code wl:evt:{eventId}}。</p>
 */
public final class WaitlistKey {

    private static final String QUEUE_PREFIX = "waitlist:";
    private static final String SEQ_PREFIX = "waitlist:seq:";
    private static final String COUNT_PREFIX = "waitlist:count:";
    private static final String RETRY_PREFIX = "waitlist:retry:";
    private static final String RETRY_INDEX = "waitlist:retry:buckets";
    private static final String FAIL_PREFIX = "waitlist:fail:";
    private static final String MATCH_LOCK_PREFIX = "waitlist:match:key:";
    private static final String MATCH_PENDING_PREFIX = "waitlist:match:pending:";
    private static final String MATCH_PENDING_INDEX = "waitlist:match:pending:buckets";
    private static final String EVENT_PREFIX = "wl:evt:";

    private WaitlistKey() {
    }

    public static String bucket(String date, String trainCode, String start, String end, String seatType) {
        return date + ":" + trainCode + ":" + start + ":" + end + ":" + seatType;
    }

    /** 主队列 */
    public static String queue(String bucket) {
        return QUEUE_PREFIX + bucket;
    }

    /** 取号器 */
    public static String seq(String bucket) {
        return SEQ_PREFIX + bucket;
    }

    /** 等待计数（新购票前检查候补优先） */
    public static String count(String bucket) {
        return COUNT_PREFIX + bucket;
    }

    /** 匹配失败退避队列：ZSet，score=可重试时间(ms)，member=applicationId */
    public static String retry(String bucket) {
        return RETRY_PREFIX + bucket;
    }

    /** 连续失败计数，10 分钟过期 */
    public static String fail(Long applicationId) {
        return FAIL_PREFIX + applicationId;
    }

    /** 按票档的匹配合并锁，2 秒 TTL */
    public static String matchLock(String bucket) {
        return MATCH_LOCK_PREFIX + bucket;
    }

    /** 释放事件幂等标记，10 分钟过期 */
    public static String event(String eventId) {
        return EVENT_PREFIX + eventId;
    }

    /** 退避队列扫描用索引 Set：member 为有退避队列的票档 bucket */
    public static String retryIndex() {
        return RETRY_INDEX;
    }

    /**
     * 待补位标记：抢锁失败说明同票档已有一轮在跑，先登记由持锁方或巡检继续消费，
     * 避免批量释放的事件被直接丢弃。
     */
    public static String matchPending(String bucket) {
        return MATCH_PENDING_PREFIX + bucket;
    }

    /** 待补位标记索引 Set：member 为有 pending 标记的票档 bucket */
    public static String matchPendingIndex() {
        return MATCH_PENDING_INDEX;
    }
}
