package com.guo.business.tx;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 事务消息探针生产者（I01）。
 *
 * <p>binding {@code txProbeProducer-out-0} 在 application.yaml 中声明为
 * {@code producerType=Trans}，消息会走半消息 + 本地事务流程。</p>
 */
@Service
public class TxProbeProducer {

    /** RocketMQ binder 从该 header 取 sendMessageInTransaction 的 arg。 */
    public static final String HEADER_TRANSACTIONAL_ARGS = "TRANSACTIONAL_ARGS";

    public static final String OUTPUT_BINDING = "txProbeProducer-out-0";

    private final StreamBridge streamBridge;

    public TxProbeProducer(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public boolean send(String bizId, boolean commit) {
        Map<String, String> args = new HashMap<>(4);
        args.put("bizId", bizId);
        args.put("outcome", commit ? "COMMIT" : "ROLLBACK");
        Message<String> message = MessageBuilder
                .withPayload("{\"bizId\":\"" + bizId + "\"}")
                .setHeader(HEADER_TRANSACTIONAL_ARGS, args)
                .setHeader("KEYS", bizId)
                .build();
        return streamBridge.send(OUTPUT_BINDING, message);
    }
}
