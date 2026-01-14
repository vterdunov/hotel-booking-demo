package com.hotel.management.repository;

import com.hotel.management.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHotelId(Long hotelId);

    List<Room> findByHotelId(Long hotelId, Sort sort);

    List<Room> findAll(Sort sort);

    @Query("""
        SELECT r FROM Room r
        WHERE r.available = true
        AND r.id NOT IN (
            SELECT s.room.id FROM RoomSlot s
            WHERE s.startDate < :endDate AND s.endDate > :startDate
        )
        """)
    List<Room> findAvailableRooms(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT r FROM Room r
        WHERE r.available = true
        AND r.id NOT IN (
            SELECT s.room.id FROM RoomSlot s
            WHERE s.startDate < :endDate AND s.endDate > :startDate
        )
        ORDER BY r.timesBooked ASC, r.id ASC
        """)
    List<Room> findRecommendedRooms(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
