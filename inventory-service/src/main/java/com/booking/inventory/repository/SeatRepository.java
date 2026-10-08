package com.booking.inventory.repository;

import com.booking.inventory.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    // Default find - relies on @Version for optimistic locking.
    // This is what we use by default: no DB lock held, high throughput,
    // conflicts detected only at commit time via version mismatch.
    Optional<Seat> findByEventIdAndSeatNumber(Long eventId, String seatNumber);



}
