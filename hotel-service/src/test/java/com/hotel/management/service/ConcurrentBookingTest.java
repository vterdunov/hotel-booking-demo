package com.hotel.management.service;

import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.entity.Hotel;
import com.hotel.management.entity.Room;
import com.hotel.management.repository.HotelRepository;
import com.hotel.management.repository.RoomRepository;
import com.hotel.management.repository.RoomSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentBookingTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomSlotRepository roomSlotRepository;

    private Room testRoom;

    @BeforeEach
    void setUp() {
        roomSlotRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();

        Hotel hotel = hotelRepository.save(Hotel.builder()
                .name("Concurrent Test Hotel")
                .address("Test Address")
                .build());

        testRoom = roomRepository.save(Room.builder()
                .hotel(hotel)
                .number("101")
                .available(true)
                .timesBooked(0)
                .build());
    }

    @Test
    @DisplayName("Sequential bookings with overlapping dates - second should fail")
    void sequentialBookings_OverlappingDates_SecondFails() {
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = LocalDate.now().plusDays(15);

        // First booking
        ConfirmAvailabilityRequest firstRequest = ConfirmAvailabilityRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .requestId("first-request")
                .build();

        boolean firstResult = roomService.confirmAvailability(testRoom.getId(), firstRequest);
        assertThat(firstResult).isTrue();

        // Second booking with overlapping dates should fail
        ConfirmAvailabilityRequest secondRequest = ConfirmAvailabilityRequest.builder()
                .startDate(startDate.plusDays(1))
                .endDate(endDate.plusDays(1))
                .requestId("second-request")
                .build();

        boolean secondResult = roomService.confirmAvailability(testRoom.getId(), secondRequest);
        assertThat(secondResult).isFalse();

        // Verify only one slot was created
        long slotCount = roomSlotRepository.count();
        assertThat(slotCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-overlapping concurrent bookings should all succeed")
    void concurrentBookings_NonOverlapping_AllSucceed() throws Exception {
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await();

                    // Each thread books different dates
                    LocalDate startDate = LocalDate.now().plusDays(20 + threadId * 5);
                    LocalDate endDate = startDate.plusDays(3);

                    ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                            .startDate(startDate)
                            .endDate(endDate)
                            .requestId("non-overlapping-request-" + threadId)
                            .build();

                    boolean result = roomService.confirmAvailability(testRoom.getId(), request);

                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Start all threads at the same time
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // All should succeed since dates don't overlap
        assertThat(successCount.get()).isEqualTo(numberOfThreads);

        // Verify all slots were created
        long slotCount = roomSlotRepository.count();
        assertThat(slotCount).isEqualTo(numberOfThreads);
    }

    @Test
    @DisplayName("Idempotent request should return success")
    void idempotentRequest_SameRequestId_ReturnsSuccess() {
        LocalDate startDate = LocalDate.now().plusDays(50);
        LocalDate endDate = LocalDate.now().plusDays(55);

        ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .requestId("idempotent-request")
                .build();

        // First call
        boolean firstResult = roomService.confirmAvailability(testRoom.getId(), request);
        assertThat(firstResult).isTrue();

        // Same request again (idempotent)
        boolean secondResult = roomService.confirmAvailability(testRoom.getId(), request);
        assertThat(secondResult).isTrue();

        // Verify only one slot was created
        long slotCount = roomSlotRepository.count();
        assertThat(slotCount).isEqualTo(1);
    }
}
