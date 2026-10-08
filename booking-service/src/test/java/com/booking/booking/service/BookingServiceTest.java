package com.booking.booking.service;

import com.booking.booking.dto.BookingRequestedEvent;
import com.booking.booking.entity.Booking;
import com.booking.booking.messaging.BookingEventPublisher;
import com.booking.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingEventPublisher eventPublisher;
    @InjectMocks BookingService bookingService;

    @Test
    void createBooking_savesPendingBooking_thenPublishesEventWithSameId() {
        Booking returned = bookingService.createBooking(1L, "S1", "user-1");

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        ArgumentCaptor<BookingRequestedEvent> eventCaptor = ArgumentCaptor.forClass(BookingRequestedEvent.class);

        InOrder order = inOrder(bookingRepository, eventPublisher);
        order.verify(bookingRepository).save(bookingCaptor.capture());
        order.verify(eventPublisher).publishBookingRequested(eventCaptor.capture());

        Booking saved = bookingCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Booking.BookingStatus.PENDING);
        assertThat(saved.getEventId()).isEqualTo(1L);
        assertThat(saved.getSeatNumber()).isEqualTo("S1");
        assertThat(saved.getUserId()).isEqualTo("user-1");

        BookingRequestedEvent event = eventCaptor.getValue();
        assertThat(event.getBookingId()).isEqualTo(saved.getId());
        assertThat(event.getSeatNumber()).isEqualTo("S1");
        assertThat(returned.getId()).isEqualTo(saved.getId());
    }

    @Test
    void getBooking_returnsBooking_whenItExists() {
        Booking booking = Booking.builder().id("b-1").build();
        when(bookingRepository.findById("b-1")).thenReturn(Optional.of(booking));

        assertThat(bookingService.getBooking("b-1")).isSameAs(booking);
    }

    @Test
    void getBooking_throws_whenBookingDoesNotExist() {
        when(bookingRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }
}