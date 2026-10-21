package com.guo.business.tx;

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
 * 事务消息本地事务监听器（I01 探针）。
 *
 * <p>在 {@code spring.cloud.stream.rocketmq.bindings.<binding>.producer.transactionListener}
 * 中按 bean 名引用。本地事务即写探针表：成功提交半消息，异常或显式回滚则丢弃半消息。</p>
 */
@Component("txProbeTransactionListener")
public class TxProbeTransactionListener implements TransactionListener {

    private static final Logger LOG = LoggerFactory.getLogger(TxProbeTransactionListener.class);

    private static final String OUTCOME_COMMIT = "COMMIT";
    private static final String OUTCOME_ROLLBACK = "ROLLBACK";

    private final TxProbeRepository txProbeRepository;

    public TxProbeTransactionListener(TxProbeRepository txProbeRepository) {
        this.txProbeRepository = txProbeRepository;
    }

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String transactionId = msg.getTransactionId();
        String bizId = null;
        String outcome = OUTCOME_COMMIT;
        if (arg instanceof Map) {
            Map<?, ?> args = (Map<?, ?>) arg;
            bizId = args.get("bizId") == null ? null : String.valueOf(args.get("bizId"));
            if (args.get("outcome") != null) {
                outcome = String.valueOf(args.get("outcome"));
            }
        }
        try {
            txProbeRepository.insert(transactionId, bizId, outcome, "local-tx");
            LOG.info("探针本地事务完成 transactionId={} bizId={} outcome={} payload={}",
                    transactionId, bizId, outcome, new String(msg.getBody(), StandardCharsets.UTF_8));
            return OUTCOME_ROLLBACK.equalsIgnoreCase(outcome)
                    ? LocalTransactionState.ROLLBACK_MESSAGE
                    : LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            LOG.error("探针本地事务失败，回滚半消息 transactionId={} bizId={}", transactionId, bizId, e);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String transactionId = msg.getTransactionId();
        try {
            String state = txProbeRepository.findStateByTransactionId(transactionId);
            if (state == null) {
                LOG.warn("探针回查未找到记录，返回 UNKNOW transactionId={}", transactionId);
                return LocalTransactionState.UNKNOW;
            }
            LOG.info("探针回查 transactionId={} state={}", transactionId, state);
            return OUTCOME_ROLLBACK.equalsIgnoreCase(state)
                    ? LocalTransactionState.ROLLBACK_MESSAGE
                    : LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            LOG.error("探针回查异常，返回 UNKNOW transactionId={}", transactionId, e);
            return LocalTransactionState.UNKNOW;
        }
    }
}
