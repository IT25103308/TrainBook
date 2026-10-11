package com.trainbooking.it25102327;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.dto.TrainDto;
import com.trainbooking.it25102327.dto.TrainSearchResultDto;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25102327.service.ScheduleService;
import com.trainbooking.it25102327.service.TrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainServiceTest {

    @Mock
    private TrainRepository trainRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private com.trainbooking.it25103308.service.BookingService bookingService;

    @InjectMocks
    private TrainService trainService;

    private Train sampleTrain;
    private Route sampleRoute;
    private Schedule sampleSchedule;

    @BeforeEach
    void setUp() {
        sampleTrain = Train.builder()
                .id(1L)
                .trainNumber("1015")
                .trainName("Udarata Menike")
                .totalSeats(300)
                .firstClassSeats(60)
                .secondClassSeats(240)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        sampleRoute = Route.builder()
                .id(1L)
                .origin("Colombo Fort")
                .destination("Kandy")
                .distanceKm(121)
                .train(sampleTrain)
                .build();

        sampleSchedule = Schedule.builder()
                .id(10L)
                .train(sampleTrain)
                .route(sampleRoute)
                .departureTime(LocalTime.of(8, 30))
                .arrivalTime(LocalTime.of(11, 5))
                .dayOfWeek(DayOfWeek.MONDAY)
                .firstClassFare(new BigDecimal("1500.00"))
                .secondClassFare(new BigDecimal("800.00"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Should return all trains from repository")
    void testGetAllTrains() {
        when(trainRepository.findAll()).thenReturn(List.of(sampleTrain));

        List<Train> trains = trainService.getAllTrains();

        assertNotNull(trains);
        assertEquals(1, trains.size());
        assertEquals("Udarata Menike", trains.get(0).getTrainName());
    }

    @Test
    @DisplayName("Should return train by ID when found")
    void testGetTrainById_Success() {
        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));

        Train result = trainService.getTrainById(1L);

        assertNotNull(result);
        assertEquals("1015", result.getTrainNumber());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when train not found")
    void testGetTrainById_NotFound() {
        when(trainRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trainService.getTrainById(99L));
    }

    @Test
    @DisplayName("Should create and save a new train")
    void testCreateTrain() {
        TrainDto dto = TrainDto.builder()
                .trainNumber("4077")
                .trainName("Yal Devi")
                .firstClassSeats(80)
                .secondClassSeats(320)
                .status("ON_TIME")
                .build();

        when(trainRepository.save(any(Train.class))).thenAnswer(i -> {
            Train t = i.getArgument(0);
            t.setId(2L);
            return t;
        });

        Train created = trainService.createTrain(dto);

        assertNotNull(created);
        assertEquals(2L, created.getId());
        assertEquals("4077", created.getTrainNumber());
        assertEquals("Yal Devi", created.getTrainName());
        assertEquals(400, created.getTotalSeats());
        assertEquals(Train.TrainStatus.ON_TIME, created.getStatus());
        verify(trainRepository).save(any(Train.class));
    }

    @Test
    @DisplayName("Should update train operational status")
    void testUpdateTrainStatus() {
        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        when(trainRepository.save(any(Train.class))).thenAnswer(i -> i.getArgument(0));

        Train updated = trainService.updateTrainStatus(1L, "DELAYED");

        assertNotNull(updated);
        assertEquals(Train.TrainStatus.DELAYED, updated.getStatus());
    }

    @Test
    @DisplayName("Should search available train schedules and map to DTOs")
    void testSearchTrains() {
        LocalDate travelDate = LocalDate.now().plusDays(1);
        sampleSchedule.setDayOfWeek(travelDate.getDayOfWeek());
        when(routeRepository.findAll()).thenReturn(List.of(sampleRoute));
        when(scheduleRepository.findMatchingSchedulesOnDate("Colombo Fort", "Kandy", travelDate.getDayOfWeek(),travelDate))
                .thenReturn(List.of(sampleSchedule));

        when(bookingService.getAvailableSeats(10L, travelDate, "FIRST")).thenReturn(58);
        when(bookingService.getAvailableSeats(10L, travelDate, "SECOND")).thenReturn(240);
        List<TrainSearchResultDto> results = trainService.searchTrains("Colombo Fort", "Kandy", travelDate);

        assertNotNull(results);
        assertEquals(1, results.size());
        TrainSearchResultDto result = results.get(0);
        assertEquals(10L, result.getScheduleId());
        assertEquals("Udarata Menike", result.getTrainName());
        assertEquals("Colombo Fort", result.getOrigin());
        assertEquals("Kandy", result.getDestination());
        assertEquals(58, result.getAvailableFirstClass());
        assertEquals(240, result.getAvailableSecondClass());
        assertEquals(new BigDecimal("1500.00"), result.getFirstClassFare());
    }

    @Test
    @DisplayName("Requires both stations rather than silently broadening a route")
    void testSearchTrains_DestinationOnly() {
        when(routeRepository.findAll()).thenReturn(List.of(sampleRoute));
        assertThrows(IllegalArgumentException.class, () -> trainService.searchTrains(null, "Kandy", LocalDate.now().plusDays(1)));
        verifyNoInteractions(scheduleRepository, bookingService);
    }

    @Test
    @DisplayName("Should delete an unused train without cascading passenger records")
    void testDeleteTrain() {
        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        when(scheduleRepository.findByTrainId(1L)).thenReturn(List.of());
        when(routeRepository.findAll()).thenReturn(List.of());

        trainService.deleteTrain(1L);

        verify(scheduleService, never()).deleteSchedule(any());
        verify(trainRepository).delete(sampleTrain);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ON_TIME","DELAYED","CANCELLED","MAINTENANCE","INACTIVE"})
    void statusMeaningsArePreservedWhenCreatingAndUpdatingTrains(String state) {
        when(trainRepository.save(any())).thenAnswer(i->i.getArgument(0));
        var dto=TrainDto.builder().trainNumber("42").trainName("Fixture").firstClassSeats(60).secondClassSeats(240).status(state).build();
        assertEquals(Train.TrainStatus.valueOf(state),trainService.createTrain(dto).getStatus());
        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        assertEquals(Train.TrainStatus.valueOf(state),trainService.updateTrainStatus(1L,state).getStatus());
    }
    @Test void unsupportedStatusIsRejectedInsteadOfBeingSavedAsADelay() {
        when(trainRepository.findById(1L)).thenReturn(Optional.of(sampleTrain));
        assertThrows(IllegalArgumentException.class,()->trainService.updateTrainStatus(1L,"BOGUS"));
        assertEquals(Train.TrainStatus.ON_TIME,sampleTrain.getStatus());
        verify(trainRepository,never()).save(any());
    }
}
