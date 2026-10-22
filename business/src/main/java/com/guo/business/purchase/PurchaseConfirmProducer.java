package com.guo.business.purchase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 受理事务消息生产者（I06）。
 *
 * <p>binding {@code purchaseConfirmProducer-out-0} 在 application.yaml 中声明为
 * {@code producerType=Trans}，本地事务由 {@link PurchaseConfirmTransactionListener} 执行。
 * RocketMQ 的 sendMessageInTransaction 是同步调用，因此方法返回时本地事务结果已写回 arg。</p>
 */
@Service
public class PurchaseConfirmProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PurchaseConfirmProducer.class);

    static final String OUTPUT_BINDING = "purchaseConfirmProducer-out-0";

    /** RocketMQ binder 从该 header 取 sendMessageInTransaction 的 arg。 */
    static final String HEADER_TRANSACTIONAL_ARGS = "TRANSACTIONAL_ARGS";

    private final StreamBridge streamBridge;

    public PurchaseConfirmProducer(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public PurchaseSendResult send(PurchaseAcceptContext context) {
        Map<String, Object> args = context.toArgs();
        Message<String> message = MessageBuilder
                .withPayload(context.toMessageJson())
                .setHeader(HEADER_TRANSACTIONAL_ARGS, args)
                .setHeader("KEYS", String.valueOf(context.getOrderId()))
                .build();
        try {
            boolean sent = streamBridge.send(OUTPUT_BINDING, message);
            boolean persisted = Boolean.TRUE.equals(args.get(PurchaseAcceptContext.ARG_PERSISTED));
            Exception failure = args.get(PurchaseAcceptContext.ARG_FAILURE) instanceof Exception
                    ? (Exception) args.get(PurchaseAcceptContext.ARG_FAILURE)
                    : null;
            LOG.info("受理事务消息发送 requestId={} orderId={} sent={} persisted={}",
                    context.getRequestId(), context.getOrderId(), sent, persisted);
            return new PurchaseSendResult(sent, persisted, failure);
        } catch (Exception e) {
            LOG.error("受理事务消息发送异常 requestId={} orderId={}",
                    context.getRequestId(), context.getOrderId(), e);
            return new PurchaseSendResult(false, false, e);
        }
    }
}
