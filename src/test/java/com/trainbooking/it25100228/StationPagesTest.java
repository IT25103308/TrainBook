package com.trainbooking.it25100228;
import com.trainbooking.config.SecurityConfig;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25100228.controller.*;
import com.trainbooking.it25100228.service.*;
import com.trainbooking.it25100228.dto.*;
import com.trainbooking.it25100228.model.BoardingLog;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102925.service.ServiceStatusService;
import com.trainbooking.it25102925.dto.ServiceStatusRow;
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
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StationPagesTest {
 @Configuration @EnableWebMvc @Import({SecurityConfig.class,GlobalExceptionHandler.class})static class WebOnly{
  @Bean org.springframework.security.core.userdetails.UserDetailsService users(){return new org.springframework.security.provisioning.InMemoryUserDetailsManager();}
  @Bean TicketValidationService gate(){return mock(TicketValidationService.class);}
  @Bean ServiceStatusService statuses(){return mock(ServiceStatusService.class);}
  @Bean DashboardService dashboard(){return mock(DashboardService.class);}
  @Bean com.trainbooking.it25101520.service.StaffRoleService roles(){return mock(com.trainbooking.it25101520.service.StaffRoleService.class);}
  @Bean com.trainbooking.it25101520.controller.StaffRoleController roleController(com.trainbooking.it25101520.service.StaffRoleService s){return new com.trainbooking.it25101520.controller.StaffRoleController(s);}
  @Bean TicketValidationController station(TicketValidationService g,ServiceStatusService s){return new TicketValidationController(g,s);}
  @Bean DashboardController reports(DashboardService s){return new DashboardController(s);}
  @Bean SpringTemplateEngine templates(){var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;}
  @Bean ThymeleafViewResolver views(SpringTemplateEngine e){var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v;}
 }
 AnnotationConfigWebApplicationContext ctx;MockMvc mvc;TicketValidationService gate;DashboardService dashboard;LocalDate day=LocalDate.now();
 @BeforeEach void setup(){ctx=new AnnotationConfigWebApplicationContext();ctx.setServletContext(new MockServletContext());ctx.register(WebOnly.class);ctx.refresh();mvc=MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).defaultRequest(get("/").with(user("admin@test.invalid").roles("ADMIN"))).build();gate=ctx.getBean(TicketValidationService.class);dashboard=ctx.getBean(DashboardService.class);
  when(ctx.getBean(ServiceStatusService.class).board(any())).thenReturn(List.of(new ServiceStatusRow(1L,2L,"Demo Express","9001","Demo Origin","Demo Destination",day,LocalTime.of(10,0),LocalTime.of(12,0),0,"ON_TIME",0,null,null)));
  var b=Booking.builder().id(1L).passengerName("Demo Passenger").seatClass(Booking.SeatClass.FIRST).seatNumbers("Car 01 / S03").numberOfSeats(1).travelDate(day).status(Booking.BookingStatus.CONFIRMED).schedule(Schedule.builder().train(Train.builder().trainName("Demo Express").build()).route(Route.builder().origin("Demo Origin").destination("Demo Destination").build()).build()).build();var t=Ticket.builder().id(1L).ticketNumber("TKT-2026-1").booking(b).isBoarded(true).build();when(gate.manifest(1L,day)).thenReturn(List.of(t));
  when(dashboard.getRecentBoardingLogs(false)).thenReturn(List.of(BoardingLog.builder().id(1L).scanResult(BoardingLog.ScanResult.VALID).scannedTicketNumber("TKT-2026-1").operatorEmail("staff@test.invalid").detail("Boarding recorded").serviceDate(day).scannedAt(LocalDateTime.now()).build()));
  when(dashboard.getDashboardStats()).thenReturn(DashboardStatsDto.builder().totalBookings(2L).todayRevenue(new BigDecimal("123.45")).totalRevenue(new BigDecimal("123.45")).activeTrains(1L).pendingBookings(1L).expiredPendingBookings(0L).todayBookings(2L).totalPassengers(1L).boardedTickets(1L).boardedPassengers(3L).scanAttempts(2L).build());
  when(dashboard.getRevenueByDate(any(),any())).thenReturn(Map.of("dailyRevenue",Map.of(day.toString(),new BigDecimal("123.45"))));when(dashboard.getReconciliationReport(any(),any())).thenReturn(Map.of("grossSettlementAmount",new BigDecimal("123.45"),"successfulTransactions",1L,"declinedTransactions",0L,"cancelledIntents",0L,"undatedPaymentRecords",0L));when(dashboard.recentBookings()).thenReturn(List.of(b));
 }
 @AfterEach void close(){ctx.close();}
 @Test void realStationManifestAndAdminReportsRenderActualValuesAndTokens()throws Exception{
  for(String path:List.of("/dashboard/validate","/dashboard","/dashboard/boarding-logs")){String html=mvc.perform(get(path).param("scheduleId","1").param("date",day.toString()).locale(Locale.US)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("Demo") || html.contains("Boarding recorded"));assertTrue(html.contains("name=\"_csrf\""));assertFalse(html.contains("482500"));Files.writeString(Path.of("target/uc06-"+path.replace('/','-')+".html"),html);}
  String html=mvc.perform(get("/dashboard/validate").param("scheduleId","1").param("date",day.toString())).andReturn().getResponse().getContentAsString();assertTrue(html.contains("name=\"reason\""));assertTrue(html.contains("boardingDate\" value=\""+day));
 }
 @Test void staffCanScanButCannotCorrectArchiveOrReadFinancialDashboard()throws Exception{
  mvc.perform(get("/dashboard/validate").with(user("staff").roles("STATION_STAFF"))).andExpect(status().isOk());mvc.perform(get("/dashboard").with(user("staff").roles("STATION_STAFF"))).andExpect(status().isForbidden());mvc.perform(post("/dashboard/validate/1/reset").with(user("staff").roles("STATION_STAFF")).with(csrf()).param("reason","Wrong entry")).andExpect(status().isForbidden());mvc.perform(post("/dashboard/boarding-logs/1/archive").with(user("staff").roles("STATION_STAFF")).with(csrf())).andExpect(status().isForbidden());verify(gate,never()).correctBoarding(any(),any(),any());
 }
 @Test void passengersAndAnonymousCannotScanAndCsrfIsRequired()throws Exception{
  mvc.perform(post("/api/tickets/validate").with(user("passenger").roles("PASSENGER")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(get("/dashboard/validate").with(anonymous())).andExpect(status().is3xxRedirection());mvc.perform(post("/api/tickets/validate").with(user("staff").roles("STATION_STAFF")).contentType("application/json").content("{}")).andExpect(status().isForbidden());verify(gate,never()).validateAndBoardTicket(any(),any(),any(),any());
 }
 @Test void selectedDateAndOperatorComeFromTheActualRequest()throws Exception{
  when(gate.validateAndBoardTicket(any(),any(),any(),any())).thenReturn(ValidationResultDto.builder().valid(false).message("Not found").build());mvc.perform(post("/api/tickets/validate").with(user("staff@test.invalid").roles("STATION_STAFF")).with(csrf()).contentType("application/json").content("{\"ticketNumber\":\"TKT-2026-1\",\"scheduleId\":\"1\",\"serviceDate\":\""+day+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));verify(gate).validateAndBoardTicket("TKT-2026-1",1L,day,"staff@test.invalid");
 }
 @Test void correctionRequiresAReasonAndArchiveIsAnAdminPost()throws Exception{
  mvc.perform(post("/dashboard/validate/1/reset").with(csrf())).andExpect(status().isBadRequest());mvc.perform(post("/dashboard/validate/1/reset").with(csrf()).param("reason","Wrong boarding recorded")).andExpect(status().is3xxRedirection());verify(gate).correctBoarding(1L,"Wrong boarding recorded","admin@test.invalid");mvc.perform(post("/dashboard/boarding-logs/1/archive").with(csrf())).andExpect(status().is3xxRedirection());verify(gate).archiveLog(1L,true,"admin@test.invalid");
 }
 @Test void staffProvisioningRequiresAdminAndRendersNoPassword()throws Exception{
  var roles=ctx.getBean(com.trainbooking.it25101520.service.StaffRoleService.class);when(roles.accounts()).thenReturn(List.of(com.trainbooking.it25101520.model.User.builder().id(5L).email("demo.staff@test.invalid").role(com.trainbooking.it25101520.model.User.Role.PASSENGER).password("private-hash").build()));when(roles.history()).thenReturn(List.of());String html=mvc.perform(get("/admin/accounts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("/admin/accounts/5/role"));assertTrue(html.contains("name=\"_csrf\""));assertFalse(html.contains("private-hash"));
  mvc.perform(get("/admin/accounts").with(user("coord").roles("COORDINATOR"))).andExpect(status().isForbidden());mvc.perform(post("/admin/accounts/5/role").with(user("staff").roles("STATION_STAFF")).with(csrf()).param("role","COORDINATOR").param("reason","Not allowed")).andExpect(status().isForbidden());verify(roles,never()).change(any(),any(),any(),any());
 }
}
