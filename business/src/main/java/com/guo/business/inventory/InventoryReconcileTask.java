package com.guo.business.inventory;

import cn.hutool.core.date.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * I11 定时对账：默认每分钟执行一次，覆盖今天与未来 N 天的车次。
 *
 * <p>单日对账失败只记录日志，不影响下一次调度；可用 {@code train.reconcile.enabled=false} 关闭。</p>
 */
@Component
@ConditionalOnProperty(prefix = "train.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReconcileTask {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryReconcileTask.class);

    private final InventoryReconcileService reconcileService;
    private final int daysAhead;

    public InventoryReconcileTask(InventoryReconcileService reconcileService,
                                  @Value("${train.reconcile.days-ahead:1}") int daysAhead) {
        this.reconcileService = reconcileService;
        this.daysAhead = Math.max(daysAhead, 0);
    }

    @Scheduled(fixedDelayString = "${train.reconcile.fixed-delay-ms:60000}",
            initialDelayString = "${train.reconcile.initial-delay-ms:30000}")
    public void reconcile() {
        Date today = DateUtil.beginOfDay(new Date());
        for (int i = 0; i <= daysAhead; i++) {
            Date date = i == 0 ? today : DateUtil.offsetDay(today, i);
            try {
                reconcileService.reconcileDate(date);
            } catch (Exception e) {
                LOG.error("库存对账执行失败 date={}", InventoryKey.formatDate(date), e);
            }
        }
    }
}
