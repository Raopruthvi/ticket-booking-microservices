package com.booking.booking.messaging;

import com.booking.booking.dto.BookingRequestedEvent;
import com.booking.booking.dto.BookingStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBookingRequested(BookingRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_REQUESTED_ROUTING_KEY,
                event
        );
    }

    public void publishBookingStatus(BookingStatusEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.BOOKING_STATUS_ROUTING_KEY,
                event
        );
    }
}
