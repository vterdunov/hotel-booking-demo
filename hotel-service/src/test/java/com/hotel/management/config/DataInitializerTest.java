package com.hotel.management.config;

import com.hotel.management.repository.HotelRepository;
import com.hotel.management.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "app.data.init-enabled=true")
class DataInitializerTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("Should initialize hotels and rooms on startup")
    void shouldInitializeData() {
        // Verify hotels are created
        assertThat(hotelRepository.count()).isGreaterThanOrEqualTo(3);

        // Verify rooms are created
        assertThat(roomRepository.count()).isGreaterThanOrEqualTo(17);

        // Verify specific hotel exists
        assertThat(hotelRepository.findAll())
                .anyMatch(h -> h.getName().equals("Grand Hotel Moscow"));

        // Verify rooms belong to hotels
        assertThat(roomRepository.findAll())
                .allMatch(r -> r.getHotel() != null);
    }

    @Test
    @DisplayName("Should not create duplicate data on multiple runs")
    void shouldNotDuplicateData() {
        long initialHotelCount = hotelRepository.count();
        long initialRoomCount = roomRepository.count();

        // Try to run initializer again
        DataInitializer initializer = new DataInitializer(hotelRepository, roomRepository);
        initializer.run();

        // Count should remain the same
        assertThat(hotelRepository.count()).isEqualTo(initialHotelCount);
        assertThat(roomRepository.count()).isEqualTo(initialRoomCount);
    }
}
