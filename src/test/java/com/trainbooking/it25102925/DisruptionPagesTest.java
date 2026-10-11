package com.trainbooking.it25102925;
import com.trainbooking.config.SecurityConfig;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.service.TrainService;
import com.trainbooking.it25102925.controller.*;
import com.trainbooking.it25102925.service.*;
import com.trainbooking.it25102925.model.*;
import com.trainbooking.it25102925.dto.*;
import com.trainbooking.it25103308.model.Booking;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.*;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DisruptionPagesTest {
 @Configuration @EnableWebMvc @Import({SecurityConfig.class,GlobalExceptionHandler.class})
 static class WebOnly {
  @Bean org.springframework.security.core.userdetails.UserDetailsService securityUsers(){return new org.springframework.security.provisioning.InMemoryUserDetailsManager();}
  @Bean NotificationService notifications(){return mock(NotificationService.class);}
  @Bean TrainService trains(){return mock(TrainService.class);}
  @Bean UserRepository users(){return mock(UserRepository.class);}
  @Bean ServiceStatusService statuses(){return mock(ServiceStatusService.class);}
  @Bean TrainLocationService locations(){return mock(TrainLocationService.class);}
  @Bean RebookingService rebooking(){return mock(RebookingService.class);}
  @Bean NotificationController notificationsController(NotificationService n,TrainService t,UserRepository u,ServiceStatusService s){return new NotificationController(n,t,u,s);}
  @Bean RebookingController rebookingController(RebookingService s){return new RebookingController(s);}
  @Bean TelemetryController telemetryController(ServiceStatusService s,TrainLocationService l){return new TelemetryController(s,l);}
  @Bean TrainLocationRestController rest(TrainLocationService l){return new TrainLocationRestController(l);}
  @Bean SpringTemplateEngine templates(){var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;}
  @Bean ThymeleafViewResolver views(SpringTemplateEngine e){var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v;}
 }
 AnnotationConfigWebApplicationContext ctx;MockMvc mvc;ServiceStatusService statuses;NotificationService alerts;RebookingService rebooking;TrainLocationService locations;LocalDate date=LocalDate.now().plusDays(2);
 @BeforeEach void setup(){ctx=new AnnotationConfigWebApplicationContext();ctx.setServletContext(new MockServletContext());ctx.register(WebOnly.class);ctx.refresh();mvc=MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).defaultRequest(get("/").with(user("own@test.invalid").roles("PASSENGER"))).build();
  statuses=ctx.getBean(ServiceStatusService.class);alerts=ctx.getBean(NotificationService.class);rebooking=ctx.getBean(RebookingService.class);locations=ctx.getBean(TrainLocationService.class);
  when(ctx.getBean(UserRepository.class).findByEmail("own@test.invalid")).thenReturn(Optional.of(User.builder().id(1L).email("own@test.invalid").build()));
  when(statuses.board(any())).thenReturn(List.of(new ServiceStatusRow(5L,2L,"Demo Express","9001","Demo Origin","Demo Destination",date,LocalTime.of(8,0),LocalTime.of(10,0),0,"DELAYED",20,"A demo service update",3L)));
  when(statuses.recentChanges()).thenReturn(List.of());when(locations.getAllLocations()).thenReturn(List.of());
 }
 @AfterEach void close(){ctx.close();}
 @Test void actualStatusTelemetryAndPublicBoardRenderIsoValuesAndTokens()throws Exception{
  for(String path:List.of("/notifications/status","/notifications/telemetry","/departure-board")){
   String html=mvc.perform(get(path).with(user("ops@test.invalid").roles("COORDINATOR")).locale(Locale.US).param("date",date.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   assertTrue(html.contains(date.toString()));assertTrue(html.contains("Demo Express"));if(!path.equals("/departure-board"))assertTrue(html.contains("name=\"_csrf\""));Files.writeString(Path.of("target/uc05-"+path.replace('/','-')+".html"),html);
  }
 }
 @Test void legacyNullableAlertsRenderAndOwnPostActionsUseCsrf()throws Exception{
  when(alerts.list(1L,"ALL")).thenReturn(List.of(Notification.builder().id(4L).subject("Demo cancellation").message("Retained alert").archived(null).isRead(null).emailResult("DISABLED").sentAt(LocalDateTime.now()).build()));
  String html=mvc.perform(get("/notifications")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("/notifications/4/archive"));assertTrue(html.contains("name=\"_csrf\""));Files.writeString(Path.of("target/uc05-alerts.html"),html);
  mvc.perform(post("/notifications/4/archive")).andExpect(status().isForbidden());mvc.perform(post("/notifications/4/archive").with(csrf())).andExpect(status().is3xxRedirection());verify(alerts).archive(4L,1L,true);
  mvc.perform(get("/notifications").param("filter","nonsense")).andExpect(redirectedUrl("/notifications"));
 }
 @Test void replacementFormUsesDatedIsoChoiceWithoutMutatingOnGet()throws Exception{
  var booking=Booking.builder().id(7L).travelDate(date).numberOfSeats(2).seatClass(Booking.SeatClass.FIRST).totalAmount(new java.math.BigDecimal("1800.00")).schedule(com.trainbooking.it25102327.model.Schedule.builder().train(com.trainbooking.it25102327.model.Train.builder().trainName("Cancelled Demo").build()).route(com.trainbooking.it25102327.model.Route.builder().origin("Demo Origin").destination("Demo Destination").build()).build()).build();
  when(rebooking.offer("offer","own@test.invalid")).thenReturn(RebookingToken.builder().booking(booking).expiresAt(LocalDateTime.now().plusHours(1)).build());when(rebooking.alternatives("offer","own@test.invalid")).thenReturn(List.of(new RebookingOption(8L,date,"Demo Replacement","9002","Demo Origin","Demo Destination",LocalTime.of(11,0),LocalTime.of(13,0),0,4,"FIRST")));
  String html=mvc.perform(get("/rebooking/claim/offer").locale(Locale.US)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("name=\"newTravelDate\" value=\""+date+"\""));assertTrue(html.contains("name=\"_csrf\""));verify(rebooking,never()).transfer(any(),any(),any(),any());Files.writeString(Path.of("target/uc05-rebook.html"),html);
 }
 @Test void passengersCannotDispatchOrClearPositionsAndUnsafeGetsDoNotWrite()throws Exception{
  for(String path:List.of("/notifications/status","/notifications/telemetry"))mvc.perform(get(path)).andExpect(status().isForbidden());
  mvc.perform(post("/api/trains/location/update").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(post("/api/trains/2/location/clear").with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get("/api/trains/location/update").with(user("ops").roles("COORDINATOR"))).andExpect(status().isMethodNotAllowed());verifyNoInteractions(locations);
 }
 @Test void malformedTelemetryIsRejectedBeforeAnyPositionIsStored()throws Exception{
  for(String json:List.of("{\"trainId\":1.5}","{\"trainId\":1,\"latitude\":\"NaN\"}","{\"trainId\":1,\"progress\":3.5}","{\"trainId\":1,\"serviceDate\":\"10/07/26\"}","{\"trainId\":1,\"status\":5}"))mvc.perform(post("/api/trains/location/update").with(user("ops").roles("COORDINATOR")).with(csrf()).contentType("application/json").content(json)).andExpect(status().isBadRequest());verifyNoInteractions(locations);
 }
}
