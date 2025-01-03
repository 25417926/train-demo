package com.guo.business.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString
public class Daily_Train_SeatQueryReq extends PageReq {

    private String trainCode;
}
