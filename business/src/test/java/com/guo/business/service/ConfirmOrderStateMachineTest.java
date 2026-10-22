package com.guo.business.service;

import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.exception.ConfirmOrderStateException;
import com.guo.business.mapper.Confirm_OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I07 验收：合法流转执行 CAS；非法流转被拒绝且不产生任何更新；CAS 冲突同样被拒绝。
 */
class ConfirmOrderStateMachineTest {

    private Confirm_OrderMapper confirmOrderMapper;
    private ConfirmOrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        confirmOrderMapper = mock(Confirm_OrderMapper.class);
        stateMachine = new ConfirmOrderStateMachine(confirmOrderMapper);
    }

    @Test
    void legalTransitionUsesCasWithFromStatus() {
        when(confirmOrderMapper.updateStatusCas(anyLong(), anyString(), anyString(), any(Date.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> stateMachine.transition(100L, "I", "P"));

        verify(confirmOrderMapper, times(1))
                .updateStatusCas(eq(100L), eq("I"), eq("P"), any(Date.class));
    }

    @Test
    void illegalTransitionRejectedWithoutAnyUpdate() {
        ConfirmOrderStateException e = assertThrows(ConfirmOrderStateException.class,
                () -> stateMachine.transition(101L, "Y", "S"));

        assertEquals("ORDER_STATUS_ILLEGAL_TRANSITION", e.getCode());
        assertEquals("Y", e.getFromStatus());
        assertEquals("S", e.getToStatus());
        verify(confirmOrderMapper, never()).updateStatusCas(anyLong(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void terminalStateCannotLeave() {
        assertFalse(stateMachine.canTransition("R", "Y"));
        assertFalse(stateMachine.canTransition("X", "S"));
        assertFalse(stateMachine.canTransition("F", "P"));
        assertThrows(ConfirmOrderStateException.class, () -> stateMachine.transition(102L, "R", "Y"));
        verify(confirmOrderMapper, never()).updateStatusCas(anyLong(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void casConflictIsRejected() {
        when(confirmOrderMapper.updateStatusCas(anyLong(), anyString(), anyString(), any(Date.class)))
                .thenReturn(0);

        assertThrows(ConfirmOrderStateException.class, () -> stateMachine.transition(103L, "I", "P"));
        verify(confirmOrderMapper, times(1))
                .updateStatusCas(eq(103L), eq("I"), eq("P"), any(Date.class));
    }

    @Test
    void waitlistAndCloseTransitionsAreAllowed() {
        assertTrue(stateMachine.canTransition("H", "Y"));
        assertTrue(stateMachine.canTransition("I", "D"));
        assertTrue(stateMachine.canTransition("S", "D"));
        assertTrue(stateMachine.canTransition("Y", "G"));
        assertTrue(stateMachine.canTransition("G", "R"));
        assertTrue(stateMachine.canTransition("P", "E"));
    }

    @Test
    void orderOverloadUsesCurrentStatus() {
        when(confirmOrderMapper.updateStatusCas(anyLong(), anyString(), anyString(), any(Date.class)))
                .thenReturn(1);

        Confirm_Order order = new Confirm_Order();
        order.setId(104L);
        order.setStatus("P");

        stateMachine.transition(order, ConfirmOrderStatusEnum.SUCCESS);

        verify(confirmOrderMapper, times(1))
                .updateStatusCas(eq(104L), eq("P"), eq("S"), any(Date.class));
    }
}
