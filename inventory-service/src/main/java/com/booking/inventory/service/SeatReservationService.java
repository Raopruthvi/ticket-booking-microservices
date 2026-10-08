package com.booking.inventory.service;

import com.booking.inventory.entity.ProcessedMessage;
import com.booking.inventory.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.Optional;
@Service
@RequiredArgsConstructor  // auto-generates a constructor for the final fields below
@Slf4j                     // auto-generates a `log` object (Simple Logging Facade for Java)
public class SeatReservationService {


    private final ProcessedMessageRepository processedMessageRepository;
    private final SeatBooker seatBooker;

    private static final int MAX_RETRIES = 3;

    // record = immutable data holder; getters, constructor, equals/hashCode/toString auto-generated
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
     *     If yes, return the ORIGINAL stored outcome (not just assume success).
     *  2. Read the seat (includes its current @Version value).
     *  3. If AVAILABLE, flip to BOOKED and save.
     *  4. Hibernate compares the version on save to the version in the DB.
     *     If another transaction already updated this row (and bumped the
     *     version) between our read and our write, save() throws
     *     OptimisticLockingFailureException instead of silently overwriting.
     *     steps 2 through 4 happens in the SeatBooker class
     *  5. On that failure, we retry a few times: re-read the seat fresh
     *     (now reflecting the winner's update), and if it's no longer
     *     AVAILABLE, fail cleanly instead of retrying forever.
     */
    public ReservationOutcome reserveSeat(String bookingId, Long eventId, String seatNumber) {

        Optional<ProcessedMessage> existing = processedMessageRepository.findById(bookingId);
        if (existing.isPresent()) {
            ProcessedMessage previous = existing.get();
            log.info("Booking {} already processed - returning original outcome", bookingId);
            return previous.isSuccess()
                    ? ReservationOutcome.ok()
                    : ReservationOutcome.failed(previous.getReason());
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return seatBooker.attemptReservation(bookingId, eventId, seatNumber);
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock conflict on attempt {} for seat {} (event {}) - retrying",
                        attempt, seatNumber, eventId);
                if (attempt == MAX_RETRIES) {
                    return seatBooker.recordOutcome(bookingId, ReservationOutcome.failed("SEAT_CONTENDED_TOO_MANY_RETRIES"));
                }
            }
        }

        // Unreachable in practice: every loop path above either returns on
        // success or returns on the final failed attempt. This exists only
        // because the compiler can't prove that and requires some return
        // statement after the loop.
        return ReservationOutcome.failed("UNKNOWN_ERROR");
    }


}