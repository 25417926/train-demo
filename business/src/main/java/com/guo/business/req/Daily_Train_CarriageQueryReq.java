package com.guo.business.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;


@Setter
@Getter
@ToString
public class Daily_Train_CarriageQueryReq extends PageReq {

    private String trainCode;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date;
}
