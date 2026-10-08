package com.booking.inventory.service;

import com.booking.inventory.entity.ProcessedMessage;
import com.booking.inventory.entity.Seat;
import com.booking.inventory.repository.ProcessedMessageRepository;
import com.booking.inventory.repository.SeatRepository;
import com.booking.inventory.service.SeatReservationService.ReservationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatBookerTest {

    @Mock SeatRepository seatRepository;
    @Mock ProcessedMessageRepository processedMessageRepository;
    @InjectMocks SeatBooker seatBooker;

    private Seat seatWithStatus(Seat.SeatStatus status) {
        return Seat.builder().seatNumber("S1").status(status).build();
    }

    private ProcessedMessage savedProcessedMessage() {
        ArgumentCaptor<ProcessedMessage> captor = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processedMessageRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsSeatNotFound_whenSeatDoesNotExist() {
        when(seatRepository.findByEventIdAndSeatNumber(1L, "S1")).thenReturn(Optional.empty());

        ReservationOutcome outcome = seatBooker.attemptReservation("b-1", 1L, "S1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("SEAT_NOT_FOUND");
        verify(seatRepository, never()).save(any());
        assertThat(savedProcessedMessage().isSuccess()).isFalse();
    }

    @Test
    void returnsSeatAlreadyBooked_whenSeatIsNotAvailable() {
        Seat seat = seatWithStatus(Seat.SeatStatus.BOOKED);
        when(seatRepository.findByEventIdAndSeatNumber(1L, "S1")).thenReturn(Optional.of(seat));

        ReservationOutcome outcome = seatBooker.attemptReservation("b-1", 1L, "S1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.reason()).isEqualTo("SEAT_ALREADY_BOOKED");
        verify(seatRepository, never()).save(any());
        assertThat(savedProcessedMessage().getReason()).isEqualTo("SEAT_ALREADY_BOOKED");
    }

    @Test
    void booksSeatAndRecordsSuccess_whenSeatIsAvailable() {
        Seat seat = seatWithStatus(Seat.SeatStatus.AVAILABLE);
        when(seatRepository.findByEventIdAndSeatNumber(1L, "S1")).thenReturn(Optional.of(seat));

        ReservationOutcome outcome = seatBooker.attemptReservation("b-1", 1L, "S1");

        assertThat(outcome.success()).isTrue();
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.BOOKED);
        verify(seatRepository).save(seat);
        ProcessedMessage saved = savedProcessedMessage();
        assertThat(saved.getMessageId()).isEqualTo("b-1");
        assertThat(saved.isSuccess()).isTrue();
    }
}