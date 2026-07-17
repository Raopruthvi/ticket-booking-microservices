package com.booking.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    // THIS is the key column. JPA/Hibernate auto-increments it on every UPDATE.
    // If two transactions read the same row (same version) and both try to
    // update it, only the FIRST commit succeeds. The second gets an
    // OptimisticLockException because the version it's writing back no longer
    // matches what's in the DB. This is what stops two people from both
    // successfully booking the same seat.
    @Version
    private Long version;

    public enum SeatStatus {
        AVAILABLE,
        LOCKED,     // temporarily held while booking is in progress
        BOOKED
    }
}
