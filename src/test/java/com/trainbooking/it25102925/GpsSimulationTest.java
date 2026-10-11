package com.trainbooking.it25102925;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25102925.dto.ServiceStatusRow;
import com.trainbooking.it25102925.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GpsSimulationTest {
    @Test void disabledByDefaultAndMissingWiringDoNotAlterFleetRecords(){var trains=mock(TrainRepository.class);var notifications=mock(NotificationService.class);var simulation=new GpsSimulationService(trains,notifications);assertFalse(simulation.isSimulationActive());simulation.runGpsTrackingCycle();simulation.setSimulationActive(true);simulation.runGpsTrackingCycle();verifyNoInteractions(trains,notifications);}
    @Test void explicitlyEnabledSimulationProducesScopedLabelledTelemetry(){var simulation=new GpsSimulationService(mock(TrainRepository.class),mock(NotificationService.class));var statuses=mock(ServiceStatusService.class);var locations=mock(TrainLocationService.class);ReflectionTestUtils.setField(simulation,"statuses",statuses);ReflectionTestUtils.setField(simulation,"locations",locations);simulation.setSimulationActive(true);
        var date=LocalDate.now();when(statuses.board(date)).thenReturn(List.of(new ServiceStatusRow(2L,1L,"Demo","9999","Origin","Destination",date,LocalTime.of(10,0),LocalTime.of(12,0),0,"ON_TIME",0,null,null)));
        simulation.runGpsTrackingCycle();simulation.runGpsTrackingCycle();simulation.runGpsTrackingCycle();verify(locations).report(eq(1L),eq(2L),eq(date),anyDouble(),anyDouble(),eq(45.0),eq("DELAYED"),isNull(),isNull(),eq("OUTBOUND"),isNull(),eq(30),eq(18),isNull(),eq(true),eq("demo GPS simulator"));
    }
}
