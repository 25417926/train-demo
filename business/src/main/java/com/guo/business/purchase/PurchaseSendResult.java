package com.guo.business.purchase;

/**
 * 事务消息发送结果：半消息是否发出 + 本地事务是否提交。
 *
 * <p>只有两者都为真才认为受理成功，否则受理链路必须回滚 Redis 预扣。</p>
 */
public class PurchaseSendResult {

    private final boolean sent;

    private final boolean persisted;

    private final Exception failure;

    public PurchaseSendResult(boolean sent, boolean persisted, Exception failure) {
        this.sent = sent;
        this.persisted = persisted;
        this.failure = failure;
    }

    public boolean isSent() {
        return sent;
    }

    public boolean isPersisted() {
        return persisted;
    }

    public Exception getFailure() {
        return failure;
    }

    public boolean isAccepted() {
        return sent && persisted;
    }

    public static PurchaseSendResult accepted() {
        return new PurchaseSendResult(true, true, null);
    }
}
