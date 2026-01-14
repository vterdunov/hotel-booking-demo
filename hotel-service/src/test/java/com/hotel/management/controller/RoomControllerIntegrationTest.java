package com.hotel.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.dto.CreateHotelRequest;
import com.hotel.management.dto.CreateRoomRequest;
import com.hotel.management.dto.ReleaseRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = generateToken("admin", "ADMIN");
        userToken = generateToken("user", "USER");
    }

    private String generateToken(String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Should create hotel and room as admin")
    void createHotelAndRoom_AsAdmin() throws Exception {
        // Create hotel
        CreateHotelRequest hotelRequest = CreateHotelRequest.builder()
                .name("Test Hotel")
                .address("123 Test Street")
                .build();

        String hotelResponse = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        Long hotelId = objectMapper.readTree(hotelResponse).get("id").asLong();

        // Create room
        CreateRoomRequest roomRequest = CreateRoomRequest.builder()
                .hotelId(hotelId)
                .number("101")
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("101"));
    }

    @Test
    @DisplayName("Should deny room creation for regular user")
    void createRoom_AsUser_Forbidden() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .hotelId(1L)
                .number("101")
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should confirm availability and release slot")
    void confirmAndRelease() throws Exception {
        // Create hotel and room first
        CreateHotelRequest hotelRequest = CreateHotelRequest.builder()
                .name("Test Hotel")
                .address("123 Test Street")
                .build();

        String hotelResponse = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long hotelId = objectMapper.readTree(hotelResponse).get("id").asLong();

        CreateRoomRequest roomRequest = CreateRoomRequest.builder()
                .hotelId(hotelId)
                .number("101")
                .build();

        String roomResponse = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long roomId = objectMapper.readTree(roomResponse).get("id").asLong();

        // Confirm availability
        ConfirmAvailabilityRequest confirmRequest = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("test-request-123")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk());

        // Idempotent - same request should succeed
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk());

        // Release slot
        ReleaseRequest releaseRequest = ReleaseRequest.builder()
                .requestId("test-request-123")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return conflict for overlapping bookings")
    void confirmAvailability_Conflict() throws Exception {
        // Create hotel and room first
        CreateHotelRequest hotelRequest = CreateHotelRequest.builder()
                .name("Test Hotel")
                .address("123 Test Street")
                .build();

        String hotelResponse = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long hotelId = objectMapper.readTree(hotelResponse).get("id").asLong();

        CreateRoomRequest roomRequest = CreateRoomRequest.builder()
                .hotelId(hotelId)
                .number("101")
                .build();

        String roomResponse = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long roomId = objectMapper.readTree(roomResponse).get("id").asLong();

        // First booking
        ConfirmAvailabilityRequest firstRequest = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("first-request")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk());

        // Overlapping booking should fail
        ConfirmAvailabilityRequest secondRequest = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(4))
                .requestId("second-request")
                .build();

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should get available rooms")
    void getAvailableRooms() throws Exception {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(3);

        mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + userToken)
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should get room statistics as admin")
    void getRoomStatistics_AsAdmin() throws Exception {
        // Create hotel and room first
        CreateHotelRequest hotelRequest = CreateHotelRequest.builder()
                .name("Statistics Test Hotel")
                .address("123 Test Street")
                .build();

        String hotelResponse = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long hotelId = objectMapper.readTree(hotelResponse).get("id").asLong();

        // Create rooms
        for (int i = 1; i <= 3; i++) {
            CreateRoomRequest roomRequest = CreateRoomRequest.builder()
                    .hotelId(hotelId)
                    .number("10" + i)
                    .build();

            mockMvc.perform(post("/api/rooms")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(roomRequest)))
                    .andExpect(status().isCreated());
        }

        // Get statistics
        mockMvc.perform(get("/api/rooms/statistics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].roomId").exists())
                .andExpect(jsonPath("$[0].hotelName").value("Statistics Test Hotel"))
                .andExpect(jsonPath("$[0].timesBooked").exists())
                .andExpect(jsonPath("$[0].occupancyRate").exists());
    }

    @Test
    @DisplayName("Should get room statistics filtered by hotel")
    void getRoomStatistics_FilteredByHotel() throws Exception {
        // Create hotel
        CreateHotelRequest hotelRequest = CreateHotelRequest.builder()
                .name("Filtered Hotel")
                .address("456 Filter Street")
                .build();

        String hotelResponse = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hotelRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long hotelId = objectMapper.readTree(hotelResponse).get("id").asLong();

        // Create room
        CreateRoomRequest roomRequest = CreateRoomRequest.builder()
                .hotelId(hotelId)
                .number("201")
                .build();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomRequest)))
                .andExpect(status().isCreated());

        // Get statistics filtered by hotelId
        mockMvc.perform(get("/api/rooms/statistics")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("hotelId", hotelId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].hotelId").value(hotelId));
    }

    @Test
    @DisplayName("Should deny statistics access for regular user")
    void getRoomStatistics_AsUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/rooms/statistics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
