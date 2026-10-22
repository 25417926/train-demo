package com.guo.business.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * I12 取消订单响应。
 */
@Getter
@Setter
public class OrderCancelResp {

    private Long orderId;

    /** true 表示订单此前已取消/关闭，本次未产生副作用 */
    private boolean alreadyClosed;
}
