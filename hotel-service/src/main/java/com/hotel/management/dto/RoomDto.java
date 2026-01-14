package com.hotel.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDto {
    private Long id;
    private Long hotelId;
    private String hotelName;
    private String number;
    private Boolean available;
    private Integer timesBooked;
}
