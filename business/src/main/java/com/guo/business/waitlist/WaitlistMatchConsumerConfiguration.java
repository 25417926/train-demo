package com.guo.business.waitlist;

import com.guo.business.observability.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * I16 候补匹配事件消费绑定，对应 {@code waitlistMatchConsumer-in-0}。
 *
 * <p>匹配失败已在 {@link WaitlistMatcher} 内部分级处理（回队或退避），
 * 因此消费端不抛异常，事件直接 ACK；同票档并发事件由合并锁吸收。</p>
 *
 * <p>I18：消费前从消息头恢复 traceId/requestId，补位日志与释放日志使用同一链路。</p>
 */
@Configuration
public class WaitlistMatchConsumerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistMatchConsumerConfiguration.class);

    @Bean
    public Consumer<Message<String>> waitlistMatchConsumer(WaitlistMatcher matcher) {
        return message -> {
            try (TraceContext.Scope ignored = TraceContext.open(
                    header(message, TraceContext.MQ_TRACE_HEADER),
                    header(message, TraceContext.MQ_REQUEST_HEADER))) {
                try {
                    matcher.onRelease(message.getPayload());
                } catch (Exception e) {
                    // 匹配异常不阻塞释放结果，记录后 ACK，由退避与定时重试兜底
                    LOG.error("候补匹配事件处理异常，已 ACK event={}", message.getPayload(), e);
                }
            }
        };
    }

    private static String header(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        return value == null ? null : String.valueOf(value);
    }
}
