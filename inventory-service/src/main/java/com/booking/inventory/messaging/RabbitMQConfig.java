package com.booking.inventory.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BOOKING_EXCHANGE = "booking.exchange";

    // Queue this service listens on
    public static final String BOOKING_REQUESTED_QUEUE = "inventory.booking-requested.queue";
    public static final String BOOKING_REQUESTED_ROUTING_KEY = "booking.requested";

    // Dead-letter setup: if a message fails processing repeatedly
    // (e.g. exception thrown by the listener), RabbitMQ routes it here
    // instead of losing it or retrying forever. This is a standard
    // production pattern worth being able to explain in an interview.
    public static final String DLX_EXCHANGE = "booking.dlx";
    public static final String BOOKING_REQUESTED_DLQ = "inventory.booking-requested.dlq";

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue bookingRequestedDlq() {
        return QueueBuilder.durable(BOOKING_REQUESTED_DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(bookingRequestedDlq())
                .to(deadLetterExchange())
                .with(BOOKING_REQUESTED_DLQ);
    }

    @Bean
    public Queue bookingRequestedQueue() {
        return QueueBuilder.durable(BOOKING_REQUESTED_QUEUE)
                // if processing fails and retries are exhausted, forward here
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", BOOKING_REQUESTED_DLQ)
                .build();
    }

    @Bean
    public Binding bookingRequestedBinding() {
        return BindingBuilder.bind(bookingRequestedQueue())
                .to(bookingExchange())
                .with(BOOKING_REQUESTED_ROUTING_KEY);
    }

    // JSON message conversion instead of default Java serialization -
    // makes messages readable in the RabbitMQ management UI and decouples
    // services from needing identical Java classpaths.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
