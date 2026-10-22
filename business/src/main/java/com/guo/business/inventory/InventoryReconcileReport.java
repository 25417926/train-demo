package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

/**
 * I11 对账结果：按车次或按日期统计 Redis 与 MySQL 的偏差及修正量。
 */
@Getter
@Setter
public class InventoryReconcileReport {

    private String date;

    private String trainCode;

    private int trainCount;

    /** inv:t 余票字段差异数 */
    private long ticketDrift;

    /** inv:seat 座位区间差异数 */
    private long seatDrift;

    /** inventory_hold 聚合与 daily_train_seat.sell 的差异数（只记录不写库） */
    private long ledgerDrift;

    /** 本次回写 Redis 的修正条数 */
    private long fixedCount;

    private long elapsedMs;

    public void add(InventoryReconcileReport other) {
        this.trainCount += other.trainCount;
        this.ticketDrift += other.ticketDrift;
        this.seatDrift += other.seatDrift;
        this.ledgerDrift += other.ledgerDrift;
        this.fixedCount += other.fixedCount;
    }
}
