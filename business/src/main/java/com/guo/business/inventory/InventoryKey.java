package com.guo.business.inventory;

import cn.hutool.core.date.DateUtil;

import java.util.Date;

/**
 * Redis 库存 key 统一定义（I04）。
 *
 * <p>日期统一使用 {@code yyyy-MM-dd}；站序沿用 daily_train_* 的 1-based 站序。
 * 座位 sell 位图的下标是 0-based，第 i 位表示“第 i+1 站 → 第 i+2 站”区间。
 * 因此区间 [startIndex, endIndex) 对应的位图下标是 [startIndex-1, endIndex-1)。</p>
 */
public final class InventoryKey {

    /** 无法关联到订单的历史占用，用该占位值标记，避免释放时误删。 */
    public static final String LEGACY_ORDER = "LEGACY";

    private static final String TICKET_PREFIX = "inv:t";
    private static final String SEAT_PREFIX = "inv:seat";
    private static final String POOL_PREFIX = "inv:pool";
    private static final String QUAL_PREFIX = "qual";
    private static final String SEG_FIELD_PREFIX = "seg:";

    private InventoryKey() {
    }

    public static String formatDate(Date date) {
        return DateUtil.formatDate(date);
    }

    /**
     * 区间余票：inv:t:{date}:{trainCode}:{startIdx}:{endIdx}，hash 字段 ydz/edz/rw/yw。
     */
    public static String ticket(String date, String trainCode, int startIndex, int endIndex) {
        return TICKET_PREFIX + ":" + date + ":" + trainCode + ":" + startIndex + ":" + endIndex;
    }

    /**
     * 座位占用：inv:seat:{date}:{trainCode}:{seatId}，hash 字段 seg:{idx}。
     */
    public static String seat(String date, String trainCode, Long seatId) {
        return SEAT_PREFIX + ":" + date + ":" + trainCode + ":" + seatId;
    }

    /**
     * 候选座位池：inv:pool:{date}:{trainCode}:{seatType}:{startIdx}:{endIdx}。
     */
    public static String pool(String date, String trainCode, String seatType, int startIndex, int endIndex) {
        return POOL_PREFIX + ":" + date + ":" + trainCode + ":" + seatType + ":" + startIndex + ":" + endIndex;
    }

    /**
     * 购票资格：qual:{date}:{trainCode}:{passengerId}，值为 orderId。
     * 该 key 由受理链路写入，不在预热阶段生成。
     */
    public static String qualification(String date, String trainCode, Long passengerId) {
        return QUAL_PREFIX + ":" + date + ":" + trainCode + ":" + passengerId;
    }

    public static String segField(int segIndex) {
        return SEG_FIELD_PREFIX + segIndex;
    }

    public static String ticketPattern(String date, String trainCode) {
        return TICKET_PREFIX + ":" + date + ":" + trainCode + ":*";
    }

    public static String seatPattern(String date, String trainCode) {
        return SEAT_PREFIX + ":" + date + ":" + trainCode + ":*";
    }

    public static String poolPattern(String date, String trainCode) {
        return POOL_PREFIX + ":" + date + ":" + trainCode + ":*";
    }
}
