package com.guo.business.req;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * I08 死信人工重放请求。
 */
@Getter
@Setter
public class DlqReplayReq {

    @NotBlank(message = "【msgId】不能为空")
    private String msgId;
}
