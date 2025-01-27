package com.guo.business.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/5
 */

@Setter
@Getter
@ToString
public class Confirm_OrderTicketReq {
    @NotNull(message = "乘客id不能为空")
    private Long passengerId;
    @NotBlank(message = "乘客类型不能为空")
    private String passengerType;
    @NotBlank(message="乘客姓名不能为空")
    private String passengerName;
    @NotBlank(message="乘客身份证不能为空")
    private String passengerIdCard;
    @NotBlank(message="座位类型不能为空")
    private String seatTypeCode;
    /** 选座 **/
    private String seat;
}
