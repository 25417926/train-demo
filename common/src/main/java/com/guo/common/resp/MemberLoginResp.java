package com.guo.common.resp;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/14
 */
@Getter
@Setter
@ToString
public class MemberLoginResp {

    private Long id;


    private String token;
}
