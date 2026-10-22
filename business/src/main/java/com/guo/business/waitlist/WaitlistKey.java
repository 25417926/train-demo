package com.guo.business.waitlist;

/**
 * 候补 Redis key 统一定义（I15）。
 *
 * <p>票档 key 为 {@code date:trainCode:start:end:seatType}：
 * 主队列 {@code waitlist:{key}}（ZSet，score=seq_no）、
 * 取号器 {@code waitlist:seq:{key}}、等待计数 {@code waitlist:count:{key}}。</p>
 */
public final class WaitlistKey {

    private static final String QUEUE_PREFIX = "waitlist:";
    private static final String SEQ_PREFIX = "waitlist:seq:";
    private static final String COUNT_PREFIX = "waitlist:count:";

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
}
