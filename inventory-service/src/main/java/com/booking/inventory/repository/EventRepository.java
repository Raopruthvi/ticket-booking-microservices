package com.booking.inventory.repository;

import com.booking.inventory.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    //JpaRepository is a ready-made tool provided by Spring, which ALREADY contains fully-working,
    // tested code for the most common database operations:
    // save(...), findById(...), findAll(), delete(...), count(), existsById(...), and many more.
    //This feature is called Spring Data JPA

    //Need not write any sql queries. Spring automatically implements this interface at runtime
    //and connects it to your database.
    //Long is the datatype of the primary key of the target class Event(id)
}
