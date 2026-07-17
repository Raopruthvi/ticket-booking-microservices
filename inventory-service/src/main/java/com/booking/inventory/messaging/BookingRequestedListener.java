package com.booking.inventory.messaging;

import com.booking.inventory.dto.BookingRequestedEvent;
import com.booking.inventory.dto.SeatReservationResultEvent;
import com.booking.inventory.service.SeatReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRequestedListener {

    private final SeatReservationService seatReservationService;
    private final ReservationResultPublisher resultPublisher;

    @RabbitListener(queues = RabbitMQConfig.BOOKING_REQUESTED_QUEUE)
    public void handleBookingRequested(BookingRequestedEvent event) {
        log.info("Received BookingRequested: bookingId={}, seat={}, event={}",
                event.getBookingId(), event.getSeatNumber(), event.getEventId());

        SeatReservationService.ReservationOutcome outcome = seatReservationService.reserveSeat(
                event.getBookingId(), event.getEventId(), event.getSeatNumber());

        // If this throws (e.g. DB down), Spring's default retry policy kicks in
        // and, after exhausting retries, the message lands in the DLQ we
        // configured - it's not silently lost.
        resultPublisher.publish(
                SeatReservationResultEvent.builder()
                        .bookingId(event.getBookingId())
                        .eventId(event.getEventId())
                        .seatNumber(event.getSeatNumber())
                        .success(outcome.success())
                        .reason(outcome.reason())
                        .build()
        );
    }
}
