package com.trainbooking.it25102925;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102925.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainLocationServiceTest {
    TrainRepository trains;TrainLocationService service;
    @BeforeEach void setup(){trains=mock(TrainRepository.class);service=new TrainLocationService(trains,mock(RouteRepository.class),mock(NotificationService.class));when(trains.findById(1L)).thenReturn(Optional.of(Train.builder().id(1L).trainName("Demo").status(Train.TrainStatus.ON_TIME).build()));when(trains.findAll()).thenReturn(List.of(Train.builder().id(1L).build()));}
    @Test void readsDoNotInventBaselineCoordinates(){assertTrue(service.getAllLocations().isEmpty());assertTrue(service.getLocation(1L).isEmpty());}
    @Test void validReportedPositionIsReadableAndDoesNotWriteFleetStatus(){var position=service.updateLocation(1L,7.0,80.0,45.0,"ON_TIME",null,null);assertEquals(7.0,service.getLocation(1L).orElseThrow().getLatitude());assertEquals("Route not assigned",position.getRoute());verify(trains,never()).save(any());}
    @Test void malformedAndOutOfRangeCoordinatesSpeedAndProgressAreRejected(){for(Double latitude:List.of(Double.NaN,Double.POSITIVE_INFINITY,91.0,-91.0))assertThrows(IllegalArgumentException.class,()->service.updateLocation(1L,latitude,80.0,45.0,null,null,null));assertThrows(IllegalArgumentException.class,()->service.updateLocation(1L,7.0,181.0,45.0,null,null,null));assertThrows(IllegalArgumentException.class,()->service.updateLocation(1L,7.0,80.0,-1.0,null,null,null));assertThrows(IllegalArgumentException.class,()->service.updateLocation(1L,7.0,80.0,45.0,null,null,null,"OUTBOUND",null,101));}
    @Test void unknownTrainDoesNotCreateAReportedPosition(){assertThrows(ResourceNotFoundException.class,()->service.updateLocation(99L,7.0,80.0,45.0,null,null,null));assertTrue(service.getLocation(99L).isEmpty());}
    @Test void unscopedDelayCannotRewriteAllTripsOfTheTrain(){assertThrows(IllegalArgumentException.class,()->service.updateLocation(1L,7.0,80.0,45.0,"DELAYED",null,null));verify(trains,never()).save(any());}
    @Test void clearingReportedPositionDoesNotRecreateAFakeTerminalLocation(){service.updateLocation(1L,7.0,80.0,45.0,null,null,null);when(trains.existsById(1L)).thenReturn(true);service.clear(1L);assertTrue(service.getLocation(1L).isEmpty());assertTrue(service.getAllLocations().isEmpty());}
}
