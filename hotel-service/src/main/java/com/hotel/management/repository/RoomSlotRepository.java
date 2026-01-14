package com.hotel.management.repository;

import com.hotel.management.entity.RoomSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface RoomSlotRepository extends JpaRepository<RoomSlot, Long> {

    Optional<RoomSlot> findByRequestId(String requestId);

    @Query("""
        SELECT COUNT(s) > 0 FROM RoomSlot s
        WHERE s.room.id = :roomId
        AND s.startDate < :endDate
        AND s.endDate > :startDate
        """)
    boolean existsOverlappingSlot(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    void deleteByRequestId(String requestId);
}
