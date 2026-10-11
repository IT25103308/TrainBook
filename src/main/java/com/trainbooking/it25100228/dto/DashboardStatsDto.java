package com.trainbooking.it25100228.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object containing aggregated metrics and statistics for the admin dashboard.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    private Long totalBookings;
    private java.math.BigDecimal totalRevenue;
    private java.math.BigDecimal todayRevenue;
    private Long activeTrains;
    private Long pendingBookings;
    private Long todayBookings;
    private Long totalPassengers;
    private Long boardedPassengers;
    private Long boardedTickets;
    private Long scanAttempts;
    private Long expiredPendingBookings;
}
