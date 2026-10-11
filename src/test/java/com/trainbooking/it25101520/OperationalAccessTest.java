package com.trainbooking.it25101520;

import com.trainbooking.it25102925.controller.*;
import com.trainbooking.it25102925.repository.RebookingTokenRepository;
import com.trainbooking.it25102925.model.RebookingToken;
import com.trainbooking.it25102925.service.*;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationalAccessTest {
    @Test void telemetryGetCannotMutateLocation() throws Exception {
        var locations=mock(TrainLocationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new TrainLocationRestController(locations)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/trains/location/update").param("trainId","1").param("latitude","7").param("longitude","80"))
            .andExpect(status().isMethodNotAllowed());verifyNoInteractions(locations);
    }
    @Test void rebookingTokenDoesNotGrantAccessToAnotherPassengersRecord() throws Exception {
        var service=mock(RebookingService.class);
        when(service.offer("fixture","other@test.invalid")).thenThrow(new com.trainbooking.exception.ResourceNotFoundException("Replacement offer not found."));
        when(service.transfer(eq("fixture"),eq(10L),any(),eq("other@test.invalid"))).thenThrow(new com.trainbooking.exception.ResourceNotFoundException("Replacement offer not found."));
        var mvc=MockMvcBuilders.standaloneSetup(new RebookingController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/rebooking/claim/fixture").principal(()->"other@test.invalid")).andExpect(status().isNotFound());
        mvc.perform(post("/rebooking/claim/fixture").principal(()->"other@test.invalid").param("newScheduleId","10").param("newTravelDate",java.time.LocalDate.now().plusDays(1).toString())).andExpect(status().isNotFound());
        verify(service,never()).alternatives(any(),any());
    }
}
