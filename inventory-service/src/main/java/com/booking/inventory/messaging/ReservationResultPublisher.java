package com.booking.inventory.messaging;

import com.booking.inventory.dto.SeatReservationResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public static final String RESULT_ROUTING_KEY = "seat.reservation.result";

    public void publish(SeatReservationResultEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RESULT_ROUTING_KEY,
                event
        );
    }
}
