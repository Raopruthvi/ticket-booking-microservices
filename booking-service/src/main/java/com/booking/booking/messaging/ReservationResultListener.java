package com.booking.booking.messaging;

import com.booking.booking.dto.BookingStatusEvent;
import com.booking.booking.dto.SeatReservationResultEvent;
import com.booking.booking.entity.Booking;
import com.booking.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationResultListener {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    @RabbitListener(queues = RabbitMQConfig.RESERVATION_RESULT_QUEUE)
    public void handleReservationResult(SeatReservationResultEvent result) {
        log.info("Received reservation result: bookingId={}, success={}",
                result.getBookingId(), result.isSuccess());

        Booking booking = bookingRepository.findById(result.getBookingId()).orElse(null);
        if (booking == null) {
            log.warn("Received result for unknown bookingId={} - ignoring", result.getBookingId());
            return;
        }

        // Idempotency note: if this booking is already CONFIRMED/FAILED
        // (e.g. this message was redelivered), don't process it twice or
        // re-publish a duplicate notification.
        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            log.info("Booking {} already in terminal state {} - skipping", booking.getId(), booking.getStatus());
            return;
        }

        if (result.isSuccess()) {
            booking.setStatus(Booking.BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(Booking.BookingStatus.FAILED);
            booking.setFailureReason(result.getReason());
        }
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        eventPublisher.publishBookingStatus(
                BookingStatusEvent.builder()
                        .bookingId(booking.getId())
                        .userId(booking.getUserId())
                        .status(booking.getStatus().name())
                        .reason(booking.getFailureReason())
                        .build()
        );
    }
}
