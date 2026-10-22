package com.guo.business.inventory;

/**
 * 库存 Lua 脚本返回码（I05）。
 */
public enum InventoryResultCode {

    /** 预扣成功 */
    OK,
    /** requestId 已受理，或乘客已有有效资格 */
    DUPLICATE,
    /** 余票不足或候选座位耗尽 */
    NO_TICKET,
    /** 同一 releaseKey 已释放过 */
    ALREADY,
    /** 库存 key 未预热（inv:t 不存在） */
    NOT_READY,
    /** hold 不存在或已不属于当前订单 */
    MISS
}
