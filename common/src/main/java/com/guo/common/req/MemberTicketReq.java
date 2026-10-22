package com.guo.common.req;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;


@Setter
@Getter
@ToString
public class MemberTicketReq {

    /**
     * 订单id（出票时由 business 写入，用于按订单精确退票/对账）
     */
    @NotNull(message = "【订单id】不能为空")
    private Long orderId;

    /**
     * 乘客id
     */
    @NotNull(message = "【会员id】不能为空")
    private Long memberId;

    /**
     * 乘客id
     */
    @NotNull(message = "【乘客id】不能为空")
    private Long passengerId;

    /**
     * 乘客id
     */
    @NotNull(message = "【乘客名字】不能为空")
    private String passengerName;

    /**
     * 日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd",timezone = "GMT+8")
    @NotNull(message = "【日期】不能为空")
    private Date trainDate;

    /**
     * 车次编号
     */
    @NotBlank(message = "【车次编号】不能为空")
    private String trainCode;

    /**
     * 箱序
     */
    @NotNull(message = "【箱序】不能为空")
    private Integer carriageIndex;

    /**
     * 排号|01, 02
     */
    @NotBlank(message = "【排号】不能为空")
    private String seatRow;

    /**
     * 列号|枚举[SeatColumnEnum]
     */
    @NotBlank(message = "【列号】不能为空")
    private String seatCol;

    /**
     * 出发站
     */
    @NotBlank(message = "【出发站】不能为空")
    private String startStation;

    /**
     * 出发时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    @NotNull(message = "【出发时间】不能为空")
    private Date startTime;

    /**
     * 到达站
     */
    @NotBlank(message = "【到达站】不能为空")
    private String endStation;

    /**
     * 到站时间
     */
    @JsonFormat(pattern = "HH:mm:ss",timezone = "GMT+8")
    @NotNull(message = "【到站时间】不能为空")
    private Date endTime;

    /**
     * 座位类型|枚举[SeatTypeEnum]
     */
    @NotBlank(message = "【座位类型】不能为空")
    private String seatType;

}


