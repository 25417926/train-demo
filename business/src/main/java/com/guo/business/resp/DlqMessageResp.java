package com.guo.business.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * I08 死信消息视图（只读）。
 */
@Getter
@Setter
public class DlqMessageResp {

    private String msgId;

    private String topic;

    private String keys;

    private Integer reconsumeTimes;

    private Long storeTimestamp;

    private String body;
}
