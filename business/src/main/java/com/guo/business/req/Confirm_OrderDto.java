package com.guo.business.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/18
 */
@Getter
@Setter
@ToString
public class Confirm_OrderDto {

    private Date date;
    private String trainCode;
    private String LogId;
}
