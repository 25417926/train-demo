package com.guo.business.purchase;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 受理事务消息本地事务监听器（I06）。
 *
 * <p>由 {@code spring.cloud.stream.rocketmq.bindings.purchaseConfirmProducer-out-0.producer.transactionListener}
 * 按 bean 名引用。本地事务即受理落库，成功提交半消息，异常回滚。</p>
 */
@Component("purchaseConfirmTransactionListener")
public class PurchaseConfirmTransactionListener implements TransactionListener {

    private static final Logger LOG = LoggerFactory.getLogger(PurchaseConfirmTransactionListener.class);

    private final PurchaseTxWriter purchaseTxWriter;

    public PurchaseConfirmTransactionListener(PurchaseTxWriter purchaseTxWriter) {
        this.purchaseTxWriter = purchaseTxWriter;
    }

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        Map<String, Object> args = asArgs(arg);
        PurchaseAcceptContext context = PurchaseAcceptContext.fromArgs(args);
        if (context == null || context.getRequestId() == null) {
            LOG.error("受理本地事务缺少上下文 transactionId={}", msg.getTransactionId());
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        try {
            purchaseTxWriter.persist(context);
            if (args != null) {
                args.put(PurchaseAcceptContext.ARG_PERSISTED, Boolean.TRUE);
            }
            LOG.info("受理本地事务完成 requestId={} orderId={} transactionId={}",
                    context.getRequestId(), context.getOrderId(), msg.getTransactionId());
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            if (args != null) {
                args.put(PurchaseAcceptContext.ARG_PERSISTED, Boolean.FALSE);
                args.put(PurchaseAcceptContext.ARG_FAILURE, e);
            }
            LOG.error("受理本地事务失败，回滚半消息 requestId={} orderId={} transactionId={}",
                    context.getRequestId(), context.getOrderId(), msg.getTransactionId(), e);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        String requestId = PurchaseAcceptContext.requestIdOfMessage(body);
        if (requestId == null) {
            LOG.warn("受理回查无法解析 requestId transactionId={}", msg.getTransactionId());
            return LocalTransactionState.UNKNOW;
        }
        try {
            boolean exists = purchaseTxWriter.existsRequest(requestId);
            LOG.info("受理回查 requestId={} exists={} transactionId={}",
                    requestId, exists, msg.getTransactionId());
            return exists ? LocalTransactionState.COMMIT_MESSAGE : LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (Exception e) {
            LOG.error("受理回查异常 requestId={} transactionId={}", requestId, msg.getTransactionId(), e);
            return LocalTransactionState.UNKNOW;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asArgs(Object arg) {
        return arg instanceof Map ? (Map<String, Object>) arg : null;
    }
}
