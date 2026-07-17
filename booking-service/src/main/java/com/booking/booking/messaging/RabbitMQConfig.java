package com.booking.booking.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    // Routing key we PUBLISH to (Inventory Service listens on this)
    public static final String BOOKING_REQUESTED_ROUTING_KEY = "booking.requested";

    // Queue we CONSUME from (Inventory Service publishes results here)
    public static final String RESERVATION_RESULT_QUEUE = "booking.reservation-result.queue";
    public static final String RESERVATION_RESULT_ROUTING_KEY = "seat.reservation.result";

    // Routing key we PUBLISH to (Notification Service listens on this)
    public static final String BOOKING_STATUS_ROUTING_KEY = "booking.status";

    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue reservationResultQueue() {
        return QueueBuilder.durable(RESERVATION_RESULT_QUEUE).build();
    }

    @Bean
    public Binding reservationResultBinding() {
        return BindingBuilder.bind(reservationResultQueue())
                .to(bookingExchange())
                .with(RESERVATION_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
