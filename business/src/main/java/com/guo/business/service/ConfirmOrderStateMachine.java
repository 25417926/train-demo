package com.guo.business.service;

import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.exception.ConfirmOrderStateException;
import com.guo.business.mapper.Confirm_OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * I07 订单状态机：流转表校验 + CAS 更新 + 全程日志。
 *
 * <p>流转表与 docs/项目计划.md 3.3 节一致。非法流转直接拒绝且不产生任何数据变更；
 * CAS 以 fromStatus 为条件，避免并发下用旧状态覆盖新状态。</p>
 */
@Service
public class ConfirmOrderStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmOrderStateMachine.class);

    private static final Map<ConfirmOrderStatusEnum, Set<ConfirmOrderStatusEnum>> TRANSITIONS;

    static {
        Map<ConfirmOrderStatusEnum, Set<ConfirmOrderStatusEnum>> transitions =
                new EnumMap<>(ConfirmOrderStatusEnum.class);
        transitions.put(ConfirmOrderStatusEnum.INIT,
                EnumSet.of(ConfirmOrderStatusEnum.PENDING, ConfirmOrderStatusEnum.EMPTY,
                        ConfirmOrderStatusEnum.FAIL, ConfirmOrderStatusEnum.CANCEL,
                        ConfirmOrderStatusEnum.CLOSED));
        transitions.put(ConfirmOrderStatusEnum.PENDING,
                EnumSet.of(ConfirmOrderStatusEnum.SUCCESS, ConfirmOrderStatusEnum.EMPTY,
                        ConfirmOrderStatusEnum.FAIL));
        transitions.put(ConfirmOrderStatusEnum.SUCCESS,
                EnumSet.of(ConfirmOrderStatusEnum.PAID, ConfirmOrderStatusEnum.CANCEL,
                        ConfirmOrderStatusEnum.CLOSED));
        transitions.put(ConfirmOrderStatusEnum.WAITLIST_PAY,
                EnumSet.of(ConfirmOrderStatusEnum.PAID, ConfirmOrderStatusEnum.CLOSED));
        transitions.put(ConfirmOrderStatusEnum.PAID,
                EnumSet.of(ConfirmOrderStatusEnum.REFUNDING, ConfirmOrderStatusEnum.CHANGED));
        transitions.put(ConfirmOrderStatusEnum.REFUNDING,
                EnumSet.of(ConfirmOrderStatusEnum.REFUNDED, ConfirmOrderStatusEnum.PAID));
        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private final Confirm_OrderMapper confirmOrderMapper;

    public ConfirmOrderStateMachine(Confirm_OrderMapper confirmOrderMapper) {
        this.confirmOrderMapper = confirmOrderMapper;
    }

    /**
     * 校验并执行状态流转；非法流转或 CAS 冲突时抛出 {@link ConfirmOrderStateException}。
     */
    public void transition(Confirm_Order order, ConfirmOrderStatusEnum target) {
        if (order == null) {
            throw new IllegalArgumentException("订单不能为空");
        }
        transition(order.getId(), order.getStatus(), target.getCode());
    }

    /**
     * 按 fromStatus 做 CAS 更新，只有当前状态仍是 fromStatus 时才会写入。
     */
    public void transition(Long orderId, String fromStatus, String toStatus) {
        if (!canTransition(fromStatus, toStatus)) {
            LOG.warn("订单状态非法流转被拒绝 orderId={} from={} to={}", orderId, fromStatus, toStatus);
            throw new ConfirmOrderStateException(orderId, fromStatus, toStatus);
        }
        int rows = confirmOrderMapper.updateStatusCas(orderId, fromStatus, toStatus, new Date());
        if (rows != 1) {
            LOG.warn("订单状态CAS更新失败（订单不存在或已被并发修改） orderId={} from={} to={} rows={}",
                    orderId, fromStatus, toStatus, rows);
            throw new ConfirmOrderStateException(orderId, fromStatus, toStatus);
        }
        LOG.info("订单状态流转成功 orderId={} {}->{}", orderId, fromStatus, toStatus);
    }

    public boolean canTransition(String fromStatus, String toStatus) {
        ConfirmOrderStatusEnum from = ConfirmOrderStatusEnum.fromCode(fromStatus, null);
        ConfirmOrderStatusEnum to = ConfirmOrderStatusEnum.fromCode(toStatus, null);
        return TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
    }
}
