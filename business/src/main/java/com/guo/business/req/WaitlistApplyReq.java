package com.guo.business.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * 候补申请请求（I15）。
 */
@Getter
@Setter
public class WaitlistApplyReq {

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @NotNull(message = "乘车日期不能为空")
    private Date date;

    @NotBlank(message = "车次不能为空")
    private String trainCode;

    @NotBlank(message = "出发站不能为空")
    private String start;

    @NotBlank(message = "到达站不能为空")
    private String end;

    @NotBlank(message = "席别不能为空")
    private String seatType;

    @NotNull(message = "乘客不能为空")
    private Long passengerId;
}
