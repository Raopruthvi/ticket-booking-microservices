package com.booking.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    private String id; // UUID string - this doubles as our idempotency key across the whole flow

    private Long eventId;

    private String seatNumber;

    private String userId;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum BookingStatus {
        PENDING,     // request received, waiting on Inventory Service
        CONFIRMED,   // seat successfully reserved
        FAILED       // seat unavailable or an error occurred
    }
}
