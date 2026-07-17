package com.booking.inventory.service;

import com.booking.inventory.entity.ProcessedMessage;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.ProcessedMessageRepository;
import com.booking.inventory.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReservationService {

    private final SeatRepository seatRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    private static final int MAX_RETRIES = 3;

    public record ReservationOutcome(boolean success, String reason) {
        public static ReservationOutcome ok() {
            return new ReservationOutcome(true, null);
        }
        public static ReservationOutcome failed(String reason) {
            return new ReservationOutcome(false, reason);
        }
    }

    /**
     * Attempts to reserve a seat. This is the method that has to survive
     * two requests racing for the same seat at the same instant.
     *
     * Flow:
     *  1. Check idempotency - have we already processed this exact bookingId?
     *     If yes, short-circuit and return the same outcome (don't reserve twice).
     *  2. Read the seat (includes its current @Version value).
     *  3. If AVAILABLE, flip to BOOKED and save.
     *  4. Hibernate compares the version on save to the version in the DB.
     *     If another transaction already updated this row (and bumped the
     *     version) between our read and our write, save() throws
     *     OptimisticLockingFailureException instead of silently overwriting.
     *  5. On that failure, we retry a few times: re-read the seat fresh
     *     (now reflecting the winner's update), and if it's no longer
     *     AVAILABLE, fail cleanly instead of retrying forever.
     */
    public ReservationOutcome reserveSeat(String bookingId, Long eventId, String seatNumber) {

        if (processedMessageRepository.existsById(bookingId)) {
            log.info("Booking {} already processed - skipping duplicate delivery", bookingId);
            // In a full implementation you'd store and return the original
            // outcome here rather than assuming success; kept simple for clarity.
            return ReservationOutcome.ok();
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return attemptReservation(bookingId, eventId, seatNumber);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock conflict on attempt {} for seat {} (event {}) - retrying",
                        attempt, seatNumber, eventId);
                if (attempt == MAX_RETRIES) {
                    return ReservationOutcome.failed("SEAT_CONTENDED_TOO_MANY_RETRIES");
                }
            }
        }
        return ReservationOutcome.failed("UNKNOWN_ERROR");
    }

    @Transactional
    protected ReservationOutcome attemptReservation(String bookingId, Long eventId, String seatNumber) {
        Optional<Seat> seatOpt = seatRepository.findByEventIdAndSeatNumber(eventId, seatNumber);

        if (seatOpt.isEmpty()) {
            return ReservationOutcome.failed("SEAT_NOT_FOUND");
        }

        Seat seat = seatOpt.get();

        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {
            return ReservationOutcome.failed("SEAT_ALREADY_BOOKED");
        }

        seat.setStatus(Seat.SeatStatus.BOOKED);
        seatRepository.save(seat); // <-- version check happens here, on flush/commit

        processedMessageRepository.save(
                ProcessedMessage.builder()
                        .messageId(bookingId)
                        .processedAt(LocalDateTime.now())
                        .build()
        );

        log.info("Seat {} for event {} successfully booked (bookingId={})", seatNumber, eventId, bookingId);
        return ReservationOutcome.ok();
    }
}
