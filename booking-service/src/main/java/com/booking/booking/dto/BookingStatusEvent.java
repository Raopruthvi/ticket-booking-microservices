package com.booking.booking.dto;

import lombok.*;

import java.io.Serializable;

// Published by this service, consumed by Notification Service
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusEvent implements Serializable {
    private String bookingId;
    private String userId;
    private String status;      // "CONFIRMED" or "FAILED"
    private String reason;      // populated only when FAILED
}
