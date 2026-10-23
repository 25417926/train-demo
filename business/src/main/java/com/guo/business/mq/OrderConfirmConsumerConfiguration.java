package com.guo.business.mq;

import com.guo.business.observability.TraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * I08 ORDER_CONFIRM 函数式消费绑定，对应 {@code purchaseConfirmConsumer-in-0}。
 *
 * <p>I18：从消息头恢复受理端写下的 traceId/requestId，使消费日志与受理日志可串联。</p>
 */
@Configuration
public class OrderConfirmConsumerConfiguration {

    @Bean
    public Consumer<Message<String>> purchaseConfirmConsumer(OrderConfirmConsumeService consumeService) {
        return message -> {
            try (TraceContext.Scope ignored = TraceContext.open(
                    header(message, TraceContext.MQ_TRACE_HEADER),
                    header(message, TraceContext.MQ_REQUEST_HEADER))) {
                consumeService.consume(message.getPayload());
            }
        };
    }

    private static String header(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        return value == null ? null : String.valueOf(value);
    }
}
