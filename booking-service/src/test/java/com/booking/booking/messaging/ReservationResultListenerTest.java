package com.booking.booking.messaging;

import com.booking.booking.dto.BookingStatusEvent;
import com.booking.booking.dto.SeatReservationResultEvent;
import com.booking.booking.entity.Booking;
import com.booking.booking.repository.BookingRepository;
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
class ReservationResultListenerTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingEventPublisher eventPublisher;
    @InjectMocks ReservationResultListener listener;

    private Booking bookingWithStatus(Booking.BookingStatus status) {
        return Booking.builder()
                .id("b-1").eventId(1L).seatNumber("S1").userId("user-1")
                .status(status).build();
    }

    @Test
    void successfulResult_confirmsBookingAndPublishesStatus() {
        Booking booking = bookingWithStatus(Booking.BookingStatus.PENDING);
        when(bookingRepository.findById("b-1")).thenReturn(Optional.of(booking));

        listener.handleReservationResult(
                SeatReservationResultEvent.builder().bookingId("b-1").success(true).build());

        assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
        verify(bookingRepository).save(booking);

        ArgumentCaptor<BookingStatusEvent> captor = ArgumentCaptor.forClass(BookingStatusEvent.class);
        verify(eventPublisher).publishBookingStatus(captor.capture());
        assertThat(captor.getValue().getBookingId()).isEqualTo("b-1");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void failedResult_marksBookingFailedWithReason() {
        Booking booking = bookingWithStatus(Booking.BookingStatus.PENDING);
        when(bookingRepository.findById("b-1")).thenReturn(Optional.of(booking));

        listener.handleReservationResult(SeatReservationResultEvent.builder()
                .bookingId("b-1").success(false).reason("SEAT_ALREADY_BOOKED").build());

        assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.FAILED);
        assertThat(booking.getFailureReason()).isEqualTo("SEAT_ALREADY_BOOKED");
        verify(bookingRepository).save(booking);

        ArgumentCaptor<BookingStatusEvent> captor = ArgumentCaptor.forClass(BookingStatusEvent.class);
        verify(eventPublisher).publishBookingStatus(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getReason()).isEqualTo("SEAT_ALREADY_BOOKED");
    }

    @Test
    void duplicateResult_forAlreadyConfirmedBooking_isIgnored() {
        Booking booking = bookingWithStatus(Booking.BookingStatus.CONFIRMED);
        when(bookingRepository.findById("b-1")).thenReturn(Optional.of(booking));

        listener.handleReservationResult(
                SeatReservationResultEvent.builder().bookingId("b-1").success(false).reason("X").build());

        assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishBookingStatus(any());
    }

    @Test
    void resultForUnknownBooking_isIgnored() {
        when(bookingRepository.findById("missing")).thenReturn(Optional.empty());

        listener.handleReservationResult(
                SeatReservationResultEvent.builder().bookingId("missing").success(true).build());

        verify(bookingRepository, never()).save(any());
        verify(eventPublisher, never()).publishBookingStatus(any());
    }
}