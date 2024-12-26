package com.guo.member.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@Setter
@Getter
@ToString
public class PassengerSaveReq {
    private Long id;
    private Long memberId;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "证件号不能为空")
    private String idCard;
    @NotBlank(message = "旅客类型不能为空")
    private String type;

    private Date createTime;

    private Date updateTime;


}