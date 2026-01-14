package com.hotel.management.controller;

import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.dto.CreateRoomRequest;
import com.hotel.management.dto.ReleaseRequest;
import com.hotel.management.dto.RoomDto;
import com.hotel.management.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management API")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @Operation(summary = "Get all available rooms for given dates")
    public ResponseEntity<List<RoomDto>> getAvailableRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(roomService.getAvailableRooms(startDate, endDate));
    }

    @GetMapping("/recommend")
    @Operation(summary = "Get recommended rooms sorted by times booked")
    public ResponseEntity<List<RoomDto>> getRecommendedRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(roomService.getRecommendedRooms(startDate, endDate));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get room by ID")
    public ResponseEntity<RoomDto> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new room (ADMIN only)")
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.createRoom(request));
    }

    @PostMapping("/{id}/confirm-availability")
    @Operation(summary = "Confirm room availability (INTERNAL)")
    public ResponseEntity<Void> confirmAvailability(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmAvailabilityRequest request) {
        boolean confirmed = roomService.confirmAvailability(id, request);
        if (confirmed) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Release room slot (INTERNAL)")
    public ResponseEntity<Void> releaseSlot(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseRequest request) {
        roomService.releaseSlot(request.getRequestId());
        return ResponseEntity.ok().build();
    }
}
