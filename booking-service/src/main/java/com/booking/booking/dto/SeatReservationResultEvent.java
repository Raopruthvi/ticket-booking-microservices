package com.booking.booking.dto;

import lombok.*;

import java.io.Serializable;

// Consumed here - published by Inventory Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReservationResultEvent implements Serializable {
    private String bookingId;
    private Long eventId;
    private String seatNumber;
    private boolean success;
    private String reason;
}
