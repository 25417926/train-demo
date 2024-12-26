package com.guo.common.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/26
 */
@Getter
@Setter
@ToString
public class PageReq {
    @NotNull(message = "页码不能为空")
    private int page;
    @NotNull(message = "每条页数不能为空")
    @Max(value = 100, message = "size不能超过100")
    private int size;
}
