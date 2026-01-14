package com.hotel.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomStatisticsDto {
    private Long roomId;
    private Long hotelId;
    private String hotelName;
    private String roomNumber;
    private Integer timesBooked;
    private Double occupancyRate;
}
