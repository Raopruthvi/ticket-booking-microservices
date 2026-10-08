package com.booking.inventory.service;

import com.booking.inventory.entity.ProcessedMessage;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.ProcessedMessageRepository;
import com.booking.inventory.repository.SeatRepository;
import com.booking.inventory.service.SeatReservationService.ReservationOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j

public class SeatBooker {
    private final SeatRepository seatRepository;
    private final ProcessedMessageRepository processedMessageRepository;


    @Transactional
    public ReservationOutcome attemptReservation(String bookingId, Long eventId, String seatNumber) {
        Optional<Seat> seatOpt = seatRepository.findByEventIdAndSeatNumber(eventId, seatNumber);

        if (seatOpt.isEmpty()) {
            return recordAndReturn(bookingId, ReservationOutcome.failed("SEAT_NOT_FOUND"));
        }

        Seat seat = seatOpt.get();

        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {
            return recordAndReturn(bookingId, ReservationOutcome.failed("SEAT_ALREADY_BOOKED"));
        }

        seat.setStatus(Seat.SeatStatus.BOOKED);
        seatRepository.save(seat); // <-- version check happens here, on flush/commit

        log.info("Seat {} for event {} successfully booked (bookingId={})", seatNumber, eventId, bookingId);
        return recordAndReturn(bookingId, ReservationOutcome.ok());
    }

    @Transactional
    public ReservationOutcome recordOutcome(String bookingId, ReservationOutcome outcome) {
        return recordAndReturn(bookingId, outcome);
    }

    // Single shared place that builds and saves a ProcessedMessage, so every
// success/failure path (including the retry-exhausted path) stores the
// real outcome - not just a "seen it" marker - enabling accurate replay
// on a duplicate delivery.
    private ReservationOutcome recordAndReturn(String bookingId, ReservationOutcome outcome) {
        processedMessageRepository.save(
                ProcessedMessage.builder()
                        .messageId(bookingId)
                        .processedAt(LocalDateTime.now())
                        .success(outcome.success())
                        .reason(outcome.reason())
                        .build()
        );
        return outcome;
    }
}