package com.guo.business.purchase;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.guo.business.req.Confirm_OrderTicketReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

/**
 * 受理请求体（POST /business/web/purchase/apply）。
 */
@Getter
@Setter
@ToString
public class PurchaseApplyReq {

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @NotNull(message = "【日期】不能为空")
    private Date date;

    @NotBlank(message = "【车次编号】不能为空")
    private String trainCode;

    @NotBlank(message = "【出发站】不能为空")
    private String start;

    @NotBlank(message = "【到达站】不能为空")
    private String end;

    @NotNull(message = "【余票ID】不能为空")
    private Long dailyTrainTicketId;

    @Valid
    @NotEmpty(message = "【车票参数】不能为空")
    private List<Confirm_OrderTicketReq> tickets;
}
