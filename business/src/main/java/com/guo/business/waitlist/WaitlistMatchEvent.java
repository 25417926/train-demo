package com.guo.business.waitlist;

import lombok.Getter;
import lombok.Setter;

/**
 * INVENTORY_RELEASED 事件（I16）：真实释放库存后触发同票档 FIFO 补位。
 *
 * <p>{@code eventId} 同时用于 outbox 审计与 {@code wl:evt:{eventId}} 幂等；
 * 其余字段构成票档 key，与 I15 的 {@code waitlist:{key}} 对齐。</p>
 */
@Getter
@Setter
public class WaitlistMatchEvent {

    /** 事件唯一id，与 order_outbox.event_id 一致 */
    private String eventId;

    private Long orderId;

    /** 关闭原因 code：T/C/R/G */
    private String reason;

    /** yyyy-MM-dd */
    private String date;

    private String trainCode;

    private String start;

    private String end;

    /** 席别编码 1/2/3/4 */
    private String seatType;

    /** 本次实际归还的座位数 */
    private int releasedSeats;

    public String bucket() {
        return WaitlistKey.bucket(date, trainCode, start, end, seatType);
    }
}
