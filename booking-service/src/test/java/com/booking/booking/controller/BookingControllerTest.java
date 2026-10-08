package com.booking.booking.controller;

import com.booking.booking.entity.Booking;
import com.booking.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean BookingService bookingService;

    @Test
    void createBooking_withValidRequest_returns202AndPending() throws Exception {
        Booking booking = Booking.builder().id("b-1").status(Booking.BookingStatus.PENDING).build();
        when(bookingService.createBooking(1L, "S1", "user-1")).thenReturn(booking);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"seatNumber\":\"S1\",\"userId\":\"user-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.bookingId").value("b-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createBooking_withMissingSeatNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"userId\":\"user-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.seatNumber").exists());
    }

    @Test
    void getBooking_forUnknownId_returns404() throws Exception {
        when(bookingService.getBooking("missing"))
                .thenThrow(new IllegalArgumentException("Booking not found: missing"));

        mockMvc.perform(get("/api/bookings/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Booking not found: missing"));
    }
}