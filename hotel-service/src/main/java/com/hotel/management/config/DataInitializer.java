package com.hotel.management.config;

import com.hotel.management.entity.Hotel;
import com.hotel.management.entity.Room;
import com.hotel.management.repository.HotelRepository;
import com.hotel.management.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.data.init-enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    public void run(String... args) {
        if (hotelRepository.count() > 0) {
            log.info("Hotels already exist, skipping data initialization");
            return;
        }

        log.info("Initializing demo data for hotels and rooms");

        // Create Hotel 1 - Grand Hotel
        Hotel grandHotel = hotelRepository.save(Hotel.builder()
                .name("Grand Hotel Moscow")
                .address("ул. Тверская, 15, Москва")
                .build());

        createRoomsForHotel(grandHotel, List.of("101", "102", "103", "201", "202", "301"));
        log.info("Created hotel '{}' with 6 rooms", grandHotel.getName());

        // Create Hotel 2 - Business Hotel
        Hotel businessHotel = hotelRepository.save(Hotel.builder()
                .name("Business Center Hotel")
                .address("Красная площадь, 1, Москва")
                .build());

        createRoomsForHotel(businessHotel, List.of("A1", "A2", "A3", "B1", "B2"));
        log.info("Created hotel '{}' with 5 rooms", businessHotel.getName());

        // Create Hotel 3 - Resort Hotel
        Hotel resortHotel = hotelRepository.save(Hotel.builder()
                .name("Seaside Resort")
                .address("ул. Приморская, 100, Сочи")
                .build());

        createRoomsForHotel(resortHotel, List.of("VIP-1", "VIP-2", "STD-101", "STD-102", "STD-103", "STD-104"));
        log.info("Created hotel '{}' with 6 rooms", resortHotel.getName());

        log.info("Demo data initialization completed: {} hotels, {} rooms",
                hotelRepository.count(), roomRepository.count());
    }

    private void createRoomsForHotel(Hotel hotel, List<String> roomNumbers) {
        for (String number : roomNumbers) {
            roomRepository.save(Room.builder()
                    .hotel(hotel)
                    .number(number)
                    .available(true)
                    .timesBooked(0)
                    .build());
        }
    }
}
