package com.booking.inventory.dto;

import lombok.*;

import java.io.Serializable;

// Published by Booking Service, consumed here in Inventory Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestedEvent implements Serializable {
    private String bookingId;      // UUID - this is our idempotency key
    private Long eventId;
    private String seatNumber;
    private String userId;
}
