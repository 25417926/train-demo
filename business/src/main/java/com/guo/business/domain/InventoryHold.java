package com.guo.business.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 库存预扣/占座账本，对应 inventory_hold 表。
 * 锁座成功时写入，status=H 表示座位仍被该订单持有。
 */
@Getter
@Setter
public class InventoryHold {

    private Long id;

    /** Redis 预扣凭据，同一订单共用同一 token */
    private String holdToken;

    /** 请求幂等键，当前用 orderId 填充 */
    private String requestId;

    private Long orderId;

    private Long memberId;

    private Long passengerId;

    private Date date;

    private String trainCode;

    /** daily_train_seat.id */
    private Long seatId;

    private String seatType;

    private Integer startIndex;

    private Integer endIndex;

    /** H 持有 R 已释放 */
    private String status;

    private Date createTime;

    private Date releaseTime;
}
