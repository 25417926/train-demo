package com.guo.business.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Daily_Train_TicketQueryReq that = (Daily_Train_TicketQueryReq) o;
        return Objects.equals(date, that.date) && Objects.equals(trainCode, that.trainCode) && Objects.equals(Start, that.Start) && Objects.equals(End, that.End) && Objects.equals(((Daily_Train_TicketQueryReq) o).getPage(), that.getPage()) && Objects.equals(((Daily_Train_TicketQueryReq) o).getSize(), that.getSize());
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, trainCode, Start, End,getPage(),getSize());
    }

    // 记录列车的终点站信息
    public String End;
}
