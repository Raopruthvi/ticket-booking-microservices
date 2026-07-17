package com.booking.booking.dto;

import lombok.*;

import java.io.Serializable;

// Published by this service, consumed by Inventory Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestedEvent implements Serializable {
    private String bookingId;
    private Long eventId;
    private String seatNumber;
    private String userId;
}
