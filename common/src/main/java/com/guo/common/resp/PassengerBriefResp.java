package com.guo.common.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 跨服务传递的乘客简要信息（business 校验候补申请归属时使用）。
 */
@Getter
@Setter
public class PassengerBriefResp {

    private Long id;

    private String name;
}
