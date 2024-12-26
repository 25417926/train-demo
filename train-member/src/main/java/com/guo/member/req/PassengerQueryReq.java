package com.guo.member.req;

import com.guo.common.req.PageReq;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PassengerQueryReq  extends PageReq {

    private Long memberId;


}