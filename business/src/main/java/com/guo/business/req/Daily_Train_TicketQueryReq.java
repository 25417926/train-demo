package com.guo.business.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
@Getter
@Setter
@ToString
public class Daily_Train_TicketQueryReq extends PageReq {
    // 使用DateTimeFormat注解来指定日期格式，确保输入的日期字符串能够被正确解析
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    public Date date;

    // 存储列车的唯一代码，用于标识特定的列车服务
    public String trainCode;

    // 记录列车的始发站信息
    public String Start;

    // 记录列车的终点站信息
    public String End;
}
