package com.trainbooking.it25102327;
import com.trainbooking.config.SecurityConfig;
import com.trainbooking.it25102327.controller.*;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25102925.service.NotificationService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.*;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationsRenderingTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class WebOnly {
        @Bean org.springframework.security.core.userdetails.UserDetailsService users(){return new org.springframework.security.provisioning.InMemoryUserDetailsManager();}
        @Bean TrainService trains(){return mock(TrainService.class);}
        @Bean ScheduleService schedules(){return mock(ScheduleService.class);}
        @Bean NotificationService notifications(){return mock(NotificationService.class);}
        @Bean org.springframework.context.MessageSource messageSource(){var source=new org.springframework.context.support.ResourceBundleMessageSource();source.setBasename("messages");source.setDefaultEncoding("UTF-8");return source;}
        @Bean TrainController trainController(TrainService t,ScheduleService s,NotificationService n){return new TrainController(t,s,n);}
        @Bean ScheduleController scheduleController(ScheduleService s,TrainService t){return new ScheduleController(s,t);}
        @Bean SpringTemplateEngine templates(){var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;}
        @Bean ThymeleafViewResolver views(SpringTemplateEngine e){var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;TrainService trains;ScheduleService schedules;
    @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).defaultRequest(get("/").with(user("coordinator@test.invalid").roles("COORDINATOR"))).build();
        trains=context.getBean(TrainService.class);schedules=context.getBean(ScheduleService.class);
        when(trains.getAllTrains()).thenReturn(List.of(Train.builder().id(1L).trainName("Demo Express").trainNumber("9999").status(Train.TrainStatus.ON_TIME).firstClassSeats(10).secondClassSeats(20).totalSeats(30).build()));
        when(trains.getStations()).thenReturn(List.of("Demo Origin","Demo Destination"));
        when(schedules.getAllRoutes()).thenReturn(List.of(Route.builder().id(1L).origin("Demo Origin").destination("Demo Destination").distanceKm(100).defaultPlatform("Platform 20").destinationPlatform("Platform 20").build()));
        ScheduleDto dto=ScheduleDto.builder().id(1L).trainId(1L).routeId(1L).trainName("Demo Express").origin("Demo Origin").destination("Demo Destination").dayOfWeek("TUESDAY")
            .departureTime("10:30").arrivalTime("12:30").arrivalDayOffset(0).farePolicy("DEMAND").departurePlatform("Platform 20").arrivalPlatform("Platform 20").isActive(true).build();
        when(schedules.getAllSchedules()).thenReturn(List.of(dto));when(schedules.getScheduleDto(1L)).thenReturn(dto);when(schedules.getOverrides(1L)).thenReturn(List.of());
    }
    @AfterEach void close(){context.close();}
    @Test void realFormsRenderCorrectActionsTokensAndBoundEditValues() throws Exception {
        when(schedules.getRouteDto(1L)).thenReturn(RouteDto.builder().origin("Demo Origin").destination("Demo Destination").distanceKm(100).defaultPlatform("Platform 20").destinationPlatform("Platform 20").build());
        for(String path:List.of("/trains/fleet","/trains/routes","/trains/routes/1/edit","/trains/schedules","/schedules/1/edit","/schedules/1/dates")){
            String html=mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("name=\"_csrf\""));assertFalse(html.contains("safely cleaned up"));
            Files.writeString(Path.of("target/ops-"+path.replace('/','-')+".html"),html);
        }
        String edit=mvc.perform(get("/trains/routes/1/edit")).andReturn().getResponse().getContentAsString();assertTrue(edit.contains("action=\"/trains/routes/1\""));assertTrue(edit.contains("value=\"Demo Origin\""));
    }
    @Test void serverValidationKeepsSubmittedFieldsAndShowsUsefulCorrections() throws Exception {
        when(schedules.createRoute(any(RouteDto.class))).thenThrow(new OperationValidation("destination","Choose different stations."));
        String html=mvc.perform(post("/trains/routes/add").with(csrf()).param("origin","Keep this name").param("destination","Keep this name").param("distanceKm","100").param("defaultPlatform","1").param("destinationPlatform","2"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("routeDto","destination")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"Keep this name\""));assertTrue(html.contains("Choose different stations."));
    }
    @Test void malformedNumbersReturnTheFormWithoutCallingTheService() throws Exception {
        String html=mvc.perform(post("/trains/add").with(csrf()).param("trainNumber","9999").param("trainName","Test Express").param("firstClassSeats","bad"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("trainDto","firstClassSeats")).andReturn().getResponse().getContentAsString();verify(trains,never()).createTrain(any());
        assertTrue(html.contains("Enter a whole number using digits."));assertFalse(html.contains("java.lang.Integer"));
    }
    @Test void aPassengerCannotReadOrMutateOperationalForms() throws Exception {
        mvc.perform(get("/trains/routes/1/edit").with(user("passenger").roles("PASSENGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/schedules/1/dates").with(user("passenger").roles("PASSENGER")).with(csrf())).andExpect(status().isForbidden());verify(schedules,never()).saveOverride(any(),any(),any());
    }
    @Test void unassignedFleetStatusDoesNotInventACorridor() throws Exception {
        mvc.perform(get("/api/trains/live-status").with(anonymous())).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].origin").value("Not assigned")).andExpect(jsonPath("$[0].destination").value("Not assigned"));
    }
}
