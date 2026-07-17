package com.booking.inventory.dto;

import lombok.*;

import java.io.Serializable;

// Published by Inventory Service, consumed by Booking Service
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
    private String reason;   // e.g. "SEAT_ALREADY_BOOKED", "SEAT_NOT_FOUND"
}
