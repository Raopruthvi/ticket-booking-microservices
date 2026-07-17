package com.booking.notification.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String BOOKING_STATUS_QUEUE = "notification.booking-status.queue";
    public static final String BOOKING_STATUS_ROUTING_KEY = "booking.status";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue bookingStatusQueue() {
        return QueueBuilder.durable(BOOKING_STATUS_QUEUE).build();
    }

    @Bean
    public Binding bookingStatusBinding() {
        return BindingBuilder.bind(bookingStatusQueue())
                .to(notificationExchange())
                .with(BOOKING_STATUS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
