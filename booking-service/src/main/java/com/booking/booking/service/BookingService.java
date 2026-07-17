package com.booking.booking.service;

import com.booking.booking.dto.BookingRequestedEvent;
import com.booking.booking.entity.Booking;
import com.booking.booking.messaging.BookingEventPublisher;
import com.booking.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    public Booking createBooking(Long eventId, String seatNumber, String userId) {
        // The bookingId is generated ONCE here and travels through the entire
        // flow (Booking -> Inventory -> back to Booking -> Notification).
        // It's what lets every service downstream do idempotent processing -
        // "have I already handled this exact bookingId before?"
        String bookingId = UUID.randomUUID().toString();

        Booking booking = Booking.builder()
                .id(bookingId)
                .eventId(eventId)
                .seatNumber(seatNumber)
                .userId(userId)
                .status(Booking.BookingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        bookingRepository.save(booking);

        eventPublisher.publishBookingRequested(
                BookingRequestedEvent.builder()
                        .bookingId(bookingId)
                        .eventId(eventId)
                        .seatNumber(seatNumber)
                        .userId(userId)
                        .build()
        );

        return booking;
    }

    public Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }
}
