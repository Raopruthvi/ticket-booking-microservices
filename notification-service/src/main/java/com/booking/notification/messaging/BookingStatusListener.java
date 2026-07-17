package com.booking.notification.messaging;

import com.booking.notification.dto.BookingStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingStatusListener {

    // In a real system this would call an email/SMS provider (SendGrid, SNS,
    // Twilio etc). For this project we log it - the point being demonstrated
    // is the event-driven decoupling, not the notification channel itself.
    @RabbitListener(queues = RabbitMQConfig.BOOKING_STATUS_QUEUE)
    public void handleBookingStatus(BookingStatusEvent event) {
        if ("CONFIRMED".equals(event.getStatus())) {
            log.info("📧 [MOCK EMAIL] To user {}: Your booking {} is CONFIRMED! Enjoy the show.",
                    event.getUserId(), event.getBookingId());
        } else {
            log.info("📧 [MOCK EMAIL] To user {}: Your booking {} FAILED. Reason: {}",
                    event.getUserId(), event.getBookingId(), event.getReason());
        }
    }
}
