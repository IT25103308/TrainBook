package com.trainbooking.it25102327;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.dto.ScheduleDto;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25102327.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private TrainRepository trainRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private com.trainbooking.it25103308.repository.BookingRepository bookingRepository;

    @Mock
    private com.trainbooking.it25100977.repository.TicketRepository ticketRepository;

    @Mock
    private com.trainbooking.it25100977.repository.PaymentRepository paymentRepository;

    @Mock
    private com.trainbooking.it25100228.repository.BoardingLogRepository boardingLogRepository;

    @Mock private com.trainbooking.it25102327.service.CatalogWriteLock catalogWriteLock;

    @InjectMocks
    private ScheduleService scheduleService;

    private Train sampleTrain;
    private Route sampleRoute;
    private Schedule sampleSchedule;

    @BeforeEach
    void setUp() {
        sampleTrain = Train.builder()
                .id(1L)
                .trainNumber("1005")
                .trainName("Podi Menike").firstClassSeats(60).secondClassSeats(240)
                .build();

        sampleRoute = Route.builder()
                .id(2L)
                .origin("Colombo Fort")
                .destination("Badulla")
                .distanceKm(290)
                .defaultPlatform("Platform 1")
                .build();

        sampleSchedule = Schedule.builder()
                .id(10L)
                .train(sampleTrain)
                .route(sampleRoute)
                .departureTime(LocalTime.of(5, 55))
                .arrivalTime(LocalTime.of(16, 7))
                .dayOfWeek(DayOfWeek.MONDAY)
                .firstClassFare(new BigDecimal("2500.00"))
                .secondClassFare(new BigDecimal("1200.00"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Should return all schedules mapped to DTOs")
    void testGetAllSchedules() {
        when(scheduleRepository.findAll()).thenReturn(List.of(sampleSchedule));

        List<ScheduleDto> dtos = scheduleService.getAllSchedules();

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals(10L, dtos.get(0).getId());
        assertEquals(1L, dtos.get(0).getTrainId());
        assertEquals(2L, dtos.get(0).getRouteId());
        assertEquals("05:55", dtos.get(0).getDepartureTime());
    }

    @Test
    @DisplayName("Should get schedule by ID")
    void testGetScheduleById_Success() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(sampleSchedule));

        Schedule schedule = scheduleService.getScheduleById(10L);

        assertNotNull(schedule);
        assertEquals(10L, schedule.getId());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when schedule ID not found")
    void testGetScheduleById_NotFound() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduleService.getScheduleById(99L));
    }

    @Test
    @DisplayName("Should create schedule successfully")
    void testCreateSchedule() {
        ScheduleDto inputDto = ScheduleDto.builder()
                .trainId(1L)
                .routeId(2L)
                .departureTime("05:55")
                .arrivalTime("16:07")
                .dayOfWeek("MONDAY")
                .firstClassFare(new BigDecimal("2500.00"))
                .secondClassFare(new BigDecimal("1200.00"))
                .isActive(true)
                .build();

        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        when(routeRepository.findById(2L)).thenReturn(Optional.of(sampleRoute));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> {
            Schedule s = i.getArgument(0);
            s.setId(15L);
            return s;
        });

        ScheduleDto result = scheduleService.createSchedule(inputDto);

        assertNotNull(result);
        assertEquals(15L, result.getId());
        assertEquals(1L, result.getTrainId());
        assertEquals(2L, result.getRouteId());
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    @DisplayName("Should delete schedule by ID")
    void testDeleteSchedule() {
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleSchedule));

        scheduleService.deleteSchedule(10L);

        verify(scheduleRepository).delete(sampleSchedule);
    }

    @Test
    @DisplayName("Should detect platform conflict when schedules share platform within 20 mins")
    void testValidatePlatformConflict_Conflict() {
        Schedule conflicting = Schedule.builder()
                .id(20L)
                .train(Train.builder().id(3L).build())
                .route(Route.builder().origin("Colombo Fort").defaultPlatform("Platform 1").build())
                .departureTime(LocalTime.of(6, 5)) // 10 minutes apart
                .arrivalTime(LocalTime.of(9,0))
                .dayOfWeek(DayOfWeek.MONDAY)
                .isActive(true)
                .build();

        when(scheduleRepository.findAll()).thenReturn(List.of(conflicting));

        String conflictMsg = scheduleService.validatePlatformConflict(sampleSchedule);
        assertNotNull(conflictMsg);
        assertTrue(conflictMsg.contains("Platform conflict"));
    }

    @Test
    @DisplayName("Should calculate dynamic fare with class and peak multipliers")
    void testCalculateDynamicFare() {
        // Base 100km, second class, off-peak Wednesday
        BigDecimal regularFare = scheduleService.calculateDynamicFare(100, "SECOND", LocalTime.of(11, 0), DayOfWeek.WEDNESDAY);
        assertEquals(new BigDecimal("500.00"), regularFare);

        // 100km, first class (1.8x)
        BigDecimal firstClassFare = scheduleService.calculateDynamicFare(100, "FIRST", LocalTime.of(11, 0), DayOfWeek.WEDNESDAY);
        assertEquals(new BigDecimal("900.00"), firstClassFare);

        // 100km, second class, morning peak (1.15x) -> 575 rounded to 580.00
        BigDecimal rushFare = scheduleService.calculateDynamicFare(100, "SECOND", LocalTime.of(7, 30), DayOfWeek.WEDNESDAY);
        assertTrue(rushFare.compareTo(regularFare) > 0);
    }

    @Test
    @DisplayName("A seasonal label alone cannot enable an actual date override")
    void testSetSeasonalOverride() {
        assertThrows(IllegalArgumentException.class,()->scheduleService.setSeasonalOverride(10L,true,"New Year Special"));
        verify(scheduleRepository,never()).save(any());
    }

    @Test
    @DisplayName("Should activate maintenance block and detect booking conflict")
    void testSetMaintenanceBlock_WithConflict() {
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleSchedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> i.getArgument(0));

        com.trainbooking.it25103308.model.Booking activeBooking = com.trainbooking.it25103308.model.Booking.builder()
                .id(100L)
                .schedule(sampleSchedule).travelDate(java.time.LocalDate.now().plusDays(1)).status(com.trainbooking.it25103308.model.Booking.BookingStatus.CONFIRMED)
                .build();
        when(bookingRepository.findByScheduleId(10L)).thenReturn(List.of(activeBooking));

        String result = scheduleService.setMaintenanceBlock(10L, true, "Brake inspection");
        assertTrue(result.contains("WARNING"));
        assertTrue(result.contains("existing booking(s) need coordinator review"));
    }

    @Test
    @DisplayName("Should retrieve schedules by train ID")
    void testGetSchedulesByTrainId() {
        when(scheduleRepository.findByTrainId(1L)).thenReturn(List.of(sampleSchedule));
        List<Schedule> list = scheduleService.getSchedulesByTrainId(1L);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(sampleSchedule.getId(), list.get(0).getId());
    }

    @Test
    @DisplayName("Should update an existing schedule successfully")
    void testUpdateSchedule_Success() {
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleSchedule));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(i -> i.getArgument(0));

        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        when(routeRepository.findById(2L)).thenReturn(Optional.of(sampleRoute));
        ScheduleDto updatePayload = ScheduleDto.builder()
                .departureTime("06:30")
                .arrivalTime("10:45")
                .dayOfWeek("FRIDAY")
                .firstClassFare(new BigDecimal("2200.00"))
                .secondClassFare(new BigDecimal("1100.00"))
                .isActive(true)
                .build();

        ScheduleDto result = scheduleService.updateSchedule(10L, updatePayload);

        assertNotNull(result);
        assertEquals("06:30", result.getDepartureTime());
        assertEquals("10:45", result.getArrivalTime());
        assertEquals("FRIDAY", result.getDayOfWeek());
        assertEquals(new BigDecimal("2200.00"), result.getFirstClassFare());
    }
}
