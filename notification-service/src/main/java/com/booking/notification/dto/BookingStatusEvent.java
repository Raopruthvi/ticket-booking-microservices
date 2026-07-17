package com.booking.notification.dto;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusEvent implements Serializable {
    private String bookingId;
    private String userId;
    private String status;
    private String reason;
}
