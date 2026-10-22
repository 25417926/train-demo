package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存预热结果，用于接口返回与日志留档。
 */
@Getter
@Setter
public class InventoryPreheatResult {

    private String date;

    /** 单车次重建时为车次号；整日预热时为 ALL。 */
    private String trainCode;

    /** 本次预热覆盖的车次数。 */
    private int trainCount;

    /** inv:t key 数量，应等于 daily_train_ticket 行数。 */
    private int ticketKeyCount;

    /** inv:seat key 数量，等于存在已售区间的座位数。 */
    private int seatKeyCount;

    /** inv:seat 中 seg 字段总数，等于 daily_train_seat.sell 中 '1' 的总数。 */
    private int occupiedSegmentCount;

    /** inv:pool key 数量。 */
    private int poolKeyCount;

    /** inv:pool 元素总数。 */
    private long poolSeatCount;

    /** 重建前删除的旧库存 key 数量。 */
    private int deletedKeyCount;

    private long elapsedMs;
}
