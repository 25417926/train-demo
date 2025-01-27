package com.guo.common.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/17
 */
@Setter
@Getter
@ToString
public class ValidReq {
    private String token;
    private String code;
    private boolean success;

}
