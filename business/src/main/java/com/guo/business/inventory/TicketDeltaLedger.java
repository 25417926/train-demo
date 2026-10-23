package com.guo.business.inventory;

import com.guo.business.mapper.OrderConfirmPersistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I21 出票余票写合并：把每单一次的 {@code daily_train_ticket} 扣减聚合成定时批量更新。
 *
 * <p>{@code daily_train_ticket} 是同一乘车区间全车次共用的一行，出票事务内直接扣减会让所有消费线程
 * 串行争抢同一行锁（jstack 实测多数线程阻塞在该 update）。这里把扣减量收敛到内存，每
 * {@link #FLUSH_INTERVAL_MS} 毫秒合并成一次 {@code update ... set field = field - n}，
 * 行锁争抢从每单一次降到每批一次。</p>
 *
 * <p>累计量拆成 {@code committed}（已登记）与 {@code applied}（已落库）两个计数，
 * 只有 MySQL 写成功才推进 {@code applied}，所以 {@code pending = committed - applied} 恒为未落库量。
 * 写库完成到 {@code applied} 推进之间 pending 会短暂偏大，对账据此跳过该行，
 * 避免用滞后的库值把 Redis 余票改回旧值。</p>
 */
@Service
public class TicketDeltaLedger implements InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(TicketDeltaLedger.class);
    private static final long FLUSH_INTERVAL_MS = 100;

    private final OrderConfirmPersistMapper persistMapper;
    private final ConcurrentHashMap<DeltaKey, Delta> deltas = new ConcurrentHashMap<>();
    private ScheduledExecutorService flusher;

    public TicketDeltaLedger(OrderConfirmPersistMapper persistMapper) {
        this.persistMapper = persistMapper;
    }

    @Override
    public void afterPropertiesSet() {
        flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ticket-delta-flush");
            thread.setDaemon(true);
            return thread;
        });
        flusher.scheduleWithFixedDelay(this::flushQuietly,
                FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
        flushQuietly();
    }

    /**
     * 登记一笔余票扣减，不访问数据库。
     */
    public void decrease(Long ticketId, String field, int delta) {
        if (ticketId == null || delta <= 0) {
            return;
        }
        deltas.computeIfAbsent(new DeltaKey(ticketId, field), key -> new Delta())
                .committed.addAndGet(delta);
    }

    /**
     * 未落库的扣减量，供对账判断该行是否可信。
     */
    public long pending(Long ticketId, String field) {
        Delta delta = deltas.get(new DeltaKey(ticketId, field));
        return delta == null ? 0 : delta.committed.get() - delta.applied.get();
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            LOG.error("余票写合并 flush 失败，下个周期重试", e);
        }
    }

    private void flush() {
        for (Map.Entry<DeltaKey, Delta> entry : deltas.entrySet()) {
            DeltaKey key = entry.getKey();
            Delta delta = entry.getValue();
            long pending = delta.committed.get() - delta.applied.get();
            if (pending <= 0) {
                continue;
            }
            try {
                persistMapper.decreaseTicket(key.ticketId, key.field, (int) pending);
                delta.applied.addAndGet(pending);
            } catch (Exception e) {
                LOG.error("余票写合并单条失败 ticketId={} field={} delta={}",
                        key.ticketId, key.field, pending, e);
            }
        }
    }

    private static final class DeltaKey {
        private final Long ticketId;
        private final String field;

        private DeltaKey(Long ticketId, String field) {
            this.ticketId = ticketId;
            this.field = field;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DeltaKey)) {
                return false;
            }
            DeltaKey other = (DeltaKey) o;
            return ticketId.equals(other.ticketId) && field.equals(other.field);
        }

        @Override
        public int hashCode() {
            return ticketId.hashCode() * 31 + field.hashCode();
        }
    }

    private static final class Delta {
        private final AtomicLong committed = new AtomicLong();
        private final AtomicLong applied = new AtomicLong();
    }
}
