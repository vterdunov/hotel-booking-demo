package com.hotel.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HotelServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "hotelService";

    private final WebClient webClient;
    private final int maxRetries;
    private final Duration timeout;

    public HotelServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${hotel-service.url:http://hotel-service}") String hotelServiceUrl,
            @Value("${hotel-service.max-retries:3}") int maxRetries,
            @Value("${hotel-service.timeout:5000}") long timeoutMs) {
        this.webClient = webClientBuilder
                .baseUrl(hotelServiceUrl)
                .build();
        this.maxRetries = maxRetries;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getRecommendedRoomsFallback")
    public List<RoomDto> getRecommendedRooms(LocalDate startDate, LocalDate endDate, String authToken) {
        log.info("Fetching recommended rooms for dates {} - {}", startDate, endDate);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/rooms/recommend")
                        .queryParam("startDate", startDate.toString())
                        .queryParam("endDate", endDate.toString())
                        .build())
                .header("Authorization", "Bearer " + authToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RoomDto>>() {})
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal ->
                                log.warn("Retrying getRecommendedRooms, attempt {}",
                                        signal.totalRetries() + 1)))
                .block();
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "confirmAvailabilityFallback")
    public boolean confirmAvailability(Long roomId, LocalDate startDate, LocalDate endDate,
                                       String requestId, String authToken) {
        log.info("Confirming availability for room {} with requestId {}", roomId, requestId);

        try {
            webClient.post()
                    .uri("/api/rooms/{id}/confirm-availability", roomId)
                    .header("Authorization", "Bearer " + authToken)
                    .bodyValue(Map.of(
                            "startDate", startDate.toString(),
                            "endDate", endDate.toString(),
                            "requestId", requestId
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        if (response.statusCode().value() == 409) {
                            return Mono.error(new RoomNotAvailableException(
                                    "Room " + roomId + " is not available"));
                        }
                        return Mono.error(new HotelServiceException(
                                "Client error: " + response.statusCode()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            Mono.error(new HotelServiceException(
                                    "Server error: " + response.statusCode())))
                    .bodyToMono(Void.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal ->
                                    log.warn("Retrying confirmAvailability for room {}, attempt {}",
                                            roomId, signal.totalRetries() + 1)))
                    .block();

            log.info("Successfully confirmed availability for room {} with requestId {}", roomId, requestId);
            return true;
        } catch (RoomNotAvailableException e) {
            log.warn("Room {} not available: {}", roomId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Failed to confirm availability for room {}: {}", roomId, e.getMessage());
            throw new HotelServiceException("Failed to confirm availability: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "releaseSlotFallback")
    public void releaseSlot(Long roomId, String requestId, String authToken) {
        log.info("Releasing slot for room {} with requestId {}", roomId, requestId);

        try {
            webClient.post()
                    .uri("/api/rooms/{id}/release", roomId)
                    .header("Authorization", "Bearer " + authToken)
                    .bodyValue(Map.of("requestId", requestId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal ->
                                    log.warn("Retrying releaseSlot for room {}, attempt {}",
                                            roomId, signal.totalRetries() + 1)))
                    .block();

            log.info("Successfully released slot for room {} with requestId {}", roomId, requestId);
        } catch (Exception e) {
            log.error("Failed to release slot for room {}: {}", roomId, e.getMessage());
        }
    }

    private boolean isRetryable(Throwable throwable) {
        return !(throwable instanceof RoomNotAvailableException);
    }

    @SuppressWarnings("unused")
    private List<RoomDto> getRecommendedRoomsFallback(LocalDate startDate, LocalDate endDate,
                                                      String authToken, Throwable t) {
        log.error("CircuitBreaker fallback: getRecommendedRooms failed for dates {} - {}: {}",
                startDate, endDate, t.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private boolean confirmAvailabilityFallback(Long roomId, LocalDate startDate, LocalDate endDate,
                                                 String requestId, String authToken, Throwable t) {
        log.error("CircuitBreaker fallback: confirmAvailability failed for room {} with requestId {}: {}",
                roomId, requestId, t.getMessage());
        throw new HotelServiceException("Hotel service unavailable: " + t.getMessage(), t);
    }

    @SuppressWarnings("unused")
    private void releaseSlotFallback(Long roomId, String requestId, String authToken, Throwable t) {
        log.error("CircuitBreaker fallback: releaseSlot failed for room {} with requestId {}: {}",
                roomId, requestId, t.getMessage());
    }

    public static class HotelServiceException extends RuntimeException {
        public HotelServiceException(String message) {
            super(message);
        }

        public HotelServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RoomNotAvailableException extends RuntimeException {
        public RoomNotAvailableException(String message) {
            super(message);
        }
    }
}
