package com.guo.business.mq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * I08 ORDER_CONFIRM 函数式消费绑定，对应 {@code purchaseConfirmConsumer-in-0}。
 */
@Configuration
public class OrderConfirmConsumerConfiguration {

    @Bean
    public Consumer<Message<String>> purchaseConfirmConsumer(OrderConfirmConsumeService consumeService) {
        return message -> consumeService.consume(message.getPayload());
    }
}
