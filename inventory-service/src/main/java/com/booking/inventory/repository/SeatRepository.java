package com.booking.inventory.repository;

import com.booking.inventory.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    // Default find - relies on @Version for optimistic locking.
    // This is what we use by default: no DB lock held, high throughput,
    // conflicts detected only at commit time via version mismatch.
    Optional<Seat> findByEventIdAndSeatNumber(Long eventId, String seatNumber);

    // Alternative: PESSIMISTIC_WRITE acquires a real row-level DB lock
    // (SELECT ... FOR UPDATE) so no other transaction can even read-for-update
    // this row until we commit/rollback. Higher safety, lower throughput -
    // good to mention as a trade-off in interviews even if you default to
    // optimistic locking in the code.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);
}
