package com.booking.inventory.dto;

import lombok.*;

import java.io.Serializable;

// Published by Booking Service, consumed here in Inventory Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestedEvent implements Serializable{ //explicitly giving java permission to save and transfer the object's state
    private String bookingId;      // UUID - this is our idempotency key
    private Long eventId;
    private String seatNumber;
    private String userId;
}
