package com.guo.member.req;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;


}