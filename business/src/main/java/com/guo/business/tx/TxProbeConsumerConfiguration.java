package com.guo.business.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 事务消息探针消费者（I01）。
 *
 * <p>函数式绑定名 {@code txProbeConsumer-in-0}，只有本地事务提交后才会收到消息；
 * 回滚的半消息不会投递到这里。</p>
 */
@Configuration
public class TxProbeConsumerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TxProbeConsumerConfiguration.class);

    @Bean
    public Consumer<Message<String>> txProbeConsumer() {
        return message -> LOG.info("探针消费到消息 headers={} payload={}",
                message.getHeaders(), message.getPayload());
    }
}
