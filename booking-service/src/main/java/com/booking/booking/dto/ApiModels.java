package com.booking.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class ApiModels {

    @Getter
    @Setter
    public static class CreateBookingRequest {
        @NotNull
        private Long eventId;

        @NotBlank
        private String seatNumber;

        @NotBlank
        private String userId;
    }

    public record BookingResponse(String bookingId, String status, String message) {}
}
