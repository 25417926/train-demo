package com.guo.member.mq;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * I10 TICKET_CREATE 函数式消费绑定，对应 {@code ticketCreateConsumer-in-0}。
 */
@Configuration
public class TicketCreateConsumerConfiguration {

    @Bean
    public Consumer<Message<String>> ticketCreateConsumer(TicketCreateConsumeService consumeService) {
        return message -> consumeService.consume(message.getPayload());
    }
}
