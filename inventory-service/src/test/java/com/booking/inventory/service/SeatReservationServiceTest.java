package com.booking.inventory.service;

import com.booking.inventory.entity.ProcessedMessage;
import com.booking.inventory.repository.ProcessedMessageRepository;
import com.booking.inventory.service.SeatReservationService.ReservationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatReservationServiceTest {

    @Mock ProcessedMessageRepository processedMessageRepository;
    @Mock SeatBooker seatBooker;
    @InjectMocks SeatReservationService service;

    @Test
    void duplicateBooking_withStoredSuccess_returnsSuccessWithoutTouchingTheSeat() {
        when(processedMessageRepository.findById("b-1")).thenReturn(Optional.of(
                ProcessedMessage.builder().messageId("b-1").success(true).build()));

        ReservationOutcome outcome = service.reserveSeat("b-1", 1L, "S1");

        assertThat(outcome.success()).isTrue();
        verifyNoInteractions(seatBooker);
    }

    @Test
    void duplicateBooking_withStoredFailure_returnsTheSameReason() {
        when(processedMessageRepository.findById("b-1")).thenReturn(Optional.of(
                ProcessedMessage.builder().messageId("b-1").success(false).reason("SEAT_ALREADY_BOOKED").build()));

        ReservationOutcome outcome = service.reserveSeat("b-1", 1L, "S1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("SEAT_ALREADY_BOOKED");
        verifyNoInteractions(seatBooker);
    }

    @Test
    void retriesAfterOneLockConflict_andThenSucceeds() {
        when(processedMessageRepository.findById("b-1")).thenReturn(Optional.empty());
        when(seatBooker.attemptReservation("b-1", 1L, "S1"))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenReturn(ReservationOutcome.ok());

        ReservationOutcome outcome = service.reserveSeat("b-1", 1L, "S1");

        assertThat(outcome.success()).isTrue();
        verify(seatBooker, times(2)).attemptReservation("b-1", 1L, "S1");
    }

    @Test
    void givesUpAfterThreeLockConflicts_withContendedReason() {
        when(processedMessageRepository.findById("b-1")).thenReturn(Optional.empty());
        when(seatBooker.attemptReservation("b-1", 1L, "S1"))
                .thenThrow(new OptimisticLockingFailureException("conflict"));
        when(seatBooker.recordOutcome(eq("b-1"), any(ReservationOutcome.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ReservationOutcome outcome = service.reserveSeat("b-1", 1L, "S1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("SEAT_CONTENDED_TOO_MANY_RETRIES");
        verify(seatBooker, times(3)).attemptReservation("b-1", 1L, "S1");
    }
}