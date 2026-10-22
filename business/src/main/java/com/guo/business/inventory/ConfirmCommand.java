package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 收尾入参（I05）：落库成功后删除 hold 并把资格续期到支付窗口。
 */
@Getter
@Setter
public class ConfirmCommand {

    private String holdToken;

    private String orderId;

    private Date date;

    private String trainCode;

    private List<Long> passengerIds;

    /** 资格续期时长，默认 30 分钟 */
    private long qualTtlMs;
}
