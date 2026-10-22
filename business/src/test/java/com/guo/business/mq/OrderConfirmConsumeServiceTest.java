package com.guo.business.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I08 验收：正常路径只消费一次、失败重投计数、达到 5 次转入死信。
 */
class OrderConfirmConsumeServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private OrderConfirmDlqSender dlqSender;
    private OrderConfirmConsumeService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        dlqSender = mock(OrderConfirmDlqSender.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new OrderConfirmConsumeService(redis, dlqSender);
    }

    @Test
    void successfulMessageConsumedOnceAndDeduped() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.consume("{\"requestId\":\"r1\",\"orderId\":1,\"holdToken\":\"t1\"}");

        verify(valueOps).setIfAbsent(startsWith("consume:order-confirm:done:r1"), eq("DONE"), any(Duration.class));
        verify(redis).delete("consume:order-confirm:fail:r1");
        verify(valueOps, never()).increment(anyString());
        verify(dlqSender, never()).send(anyString(), anyString(), any(Integer.class), anyString());
    }

    @Test
    void duplicateMessageIsSkipped() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertDoesNotThrow(() -> service.consume("{\"requestId\":\"r2\",\"orderId\":2}"));

        verify(redis, never()).delete(anyString());
        verify(valueOps, never()).increment(anyString());
        verify(dlqSender, never()).send(anyString(), anyString(), any(Integer.class), anyString());
    }

    @Test
    void attemptsWithinLimitThrowToTriggerRedelivery() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOps.increment("consume:order-confirm:fail:r3")).thenReturn(5L);

        assertThrows(IllegalStateException.class,
                () -> service.consume("{\"requestId\":\"r3\"}"));

        verify(redis).delete("consume:order-confirm:done:r3");
        verify(dlqSender, never()).send(anyString(), anyString(), any(Integer.class), anyString());
    }

    @Test
    void afterFiveRedeliveriesGoesToDeadLetter() {
        String body = "{\"requestId\":\"r4\"}";
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOps.increment("consume:order-confirm:fail:r4")).thenReturn(6L);

        assertDoesNotThrow(() -> service.consume(body));

        verify(dlqSender).send(eq(body), eq("r4"), eq(6), anyString());
    }

    @Test
    void unparsableBodyUsesDigestKeyAndStillCounts() {
        when(valueOps.setIfAbsent(startsWith("consume:order-confirm:done:"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.increment(startsWith("consume:order-confirm:fail:"))).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.consume("not-json"));
    }
}
