package com.trainbooking.it25102925;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25102925.dto.ServiceStatusRequest;
import com.trainbooking.it25102925.events.*;
import com.trainbooking.it25102925.model.*;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25102925.service.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fixed disposable H2; actual commits, observers, allocation and races. No Boot/app datasource or scheduled jobs. */
@SpringJUnitConfig(BoardingPersistenceTest.PersistenceOnly.class)
class BoardingPersistenceTest {
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={UserRepository.class,ScheduleRepository.class,BookingRepository.class,PaymentRepository.class,NotificationRepository.class,com.trainbooking.it25100228.repository.BoardingLogRepository.class})
    @Import({BookingService.class,PaymentService.class,CatalogWriteLock.class,ServiceStatusService.class,ServiceChangeSubject.class,ServiceAuditObserver.class,
        PassengerNotificationObserver.class,RebookingService.class,NotificationService.class,TrainLocationService.class,com.trainbooking.it25100228.service.TicketValidationService.class,com.trainbooking.it25100228.service.DashboardService.class,com.trainbooking.it25101520.service.StaffRoleService.class})
    static class PersistenceOnly {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:uc05_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.trainbooking");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean EmailService email(){return mock(EmailService.class);}
        @Bean org.springframework.security.core.session.SessionRegistry registry(){return new org.springframework.security.core.session.SessionRegistryImpl();}
        @Bean com.trainbooking.it25101520.service.AccountSessions sessions(org.springframework.security.core.session.SessionRegistry registry){return new com.trainbooking.it25101520.service.AccountSessions(registry);}
    }
    @Autowired BookingService bookingService;@Autowired PaymentService paymentService;@Autowired ServiceStatusService statuses;@Autowired RebookingService rebooking;
    @Autowired NotificationService alerts;@Autowired TrainLocationService locations;@Autowired EmailService email;
    @Autowired UserRepository users;@Autowired ScheduleRepository schedules;@Autowired BookingRepository bookings;@Autowired PaymentRepository payments;@Autowired TicketRepository tickets;
    @Autowired NotificationRepository notifications;@Autowired RebookingTokenRepository tokens;@Autowired ServiceDisruptionRepository disruptions;@Autowired ServiceChangeAuditRepository audit;
    @Autowired RebookingTransferRepository transfers;@Autowired PlanningLockRepository planning;@Autowired PlatformTransactionManager txManager;
    @PersistenceContext EntityManager em;
    LocalDate date;Long sourceId,targetId,userId,otherId;String owner,other;
    @BeforeEach void setup(){reset(email);when(email.sendSimpleEmail(any(),any(),any())).thenReturn(EmailService.DeliveryResult.DISABLED);
        date=LocalDate.now().plusDays(2);String suffix=UUID.randomUUID().toString().substring(0,8);owner="own-"+suffix+"@test.invalid";other="other-"+suffix+"@test.invalid";
        if(!planning.existsById(1L))planning.saveAndFlush(new PlanningLock(1L));
        tx().executeWithoutResult(s->{
            User first=User.builder().email(owner).firstName("Demo").lastName("Passenger").password("test-hash").role(User.Role.PASSENGER).notifyByEmail(false).notifyBySms(false).delayAlertThresholdMinutes(15).build();
            User second=User.builder().email(other).firstName("Other").lastName("Passenger").password("test-hash").role(User.Role.PASSENGER).notifyByEmail(false).notifyBySms(false).delayAlertThresholdMinutes(15).build();em.persist(first);em.persist(second);userId=first.getId();otherId=second.getId();
            var route=Route.builder().origin("Origin "+suffix).destination("Destination "+suffix).distanceKm(100).build();em.persist(route);
            var a=Train.builder().trainName("Source Express").trainNumber("A"+suffix).firstClassSeats(10).secondClassSeats(20).totalSeats(30).status(Train.TrainStatus.ON_TIME).build();
            var b=Train.builder().trainName("Replacement Express").trainNumber("B"+suffix).firstClassSeats(10).secondClassSeats(20).totalSeats(30).status(Train.TrainStatus.ON_TIME).build();em.persist(a);em.persist(b);
            var firstSchedule=Schedule.builder().train(a).route(route).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(8,0)).arrivalTime(LocalTime.of(10,0)).firstClassFare(new BigDecimal("900.00")).secondClassFare(new BigDecimal("500.00")).build();
            var secondSchedule=Schedule.builder().train(b).route(route).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(11,0)).arrivalTime(LocalTime.of(13,0)).firstClassFare(new BigDecimal("1200.00")).secondClassFare(new BigDecimal("600.00")).build();em.persist(firstSchedule);em.persist(secondSchedule);sourceId=firstSchedule.getId();targetId=secondSchedule.getId();
        });
    }
    TransactionTemplate tx(){var t=new TransactionTemplate(txManager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    BookingRequest request(Long schedule,LocalDate date,int quantity,String seats){return BookingRequest.builder().scheduleId(schedule).travelDate(date).seatClass("FIRST").numberOfSeats(quantity).seatNumbers(seats).passengerName("Demo Passenger").documentType("NIC").passengerNic("200300901022").contactPhone("0771234567").build();}
    Booking paid(String seats,int quantity){var b=bookingService.createBooking(request(sourceId,date,quantity,seats),userId);paymentService.payAndIssue(b.getId(),card());return b;}
    PaymentRequest card(){return PaymentRequest.builder().cardHolderName("Demo Passenger").cardNumber("4111222233334444").cvv("123").expiryMonth("12").expiryYear(String.valueOf(LocalDate.now().getYear()+1)).build();}
    ServiceStatusRequest change(String status,int delay){var r=new ServiceStatusRequest();r.setScheduleId(sourceId);r.setServiceDate(date);r.setStatus(status);r.setDelayMinutes(delay);r.setReason("Coordinator service update");return r;}
    RebookingToken cancellation(Booking booking){statuses.update(change("CANCELLED",0),"coordinator@test.invalid");return tokens.findByBookingIdAndIsRedeemedFalse(booking.getId()).stream().findFirst().orElseThrow();}


    @Autowired com.trainbooking.it25100228.service.TicketValidationService gate;
    @Autowired com.trainbooking.it25100228.service.DashboardService dashboard;
    @Autowired com.trainbooking.it25100228.repository.BoardingLogRepository scanLogs;
    @Autowired com.trainbooking.it25101520.service.StaffRoleService access;
    @Autowired com.trainbooking.it25101520.service.AccountSessions sessions;
    @Autowired org.springframework.security.core.session.SessionRegistry registry;
    @Autowired com.trainbooking.it25101520.repository.RoleChangeAuditRepository roleAudit;
    com.trainbooking.it25100977.model.Ticket boardable(int count){
      Booking b=paid(count==1?"Car 01 / S03":"Car 01 / S03, Car 01 / S04",count);LocalDateTime depart=LocalDateTime.now().withNano(0).minusMinutes(15),arrival=LocalDateTime.now().withNano(0).plusHours(1);date=depart.toLocalDate();
      tx().executeWithoutResult(s->{Booking row=em.find(Booking.class,b.getId());Schedule run=row.getSchedule();run.setDayOfWeek(date.getDayOfWeek());run.setDepartureTime(depart.toLocalTime());run.setArrivalTime(arrival.toLocalTime());int offset=(int)java.time.temporal.ChronoUnit.DAYS.between(date,arrival.toLocalDate());run.setArrivalDayOffset(offset);row.setTravelDate(date);row.setDepartureTimeSnapshot(depart.toLocalTime());row.setArrivalTimeSnapshot(arrival.toLocalTime());row.setArrivalDayOffsetSnapshot(offset);});return tickets.findByBookingId(b.getId()).orElseThrow();
    }
    @Test void paidTicketBoardsOnceAndMetricsCountItsPassengers(){
      var before=dashboard.getDashboardStats();var ticket=boardable(2);assertTrue(gate.validateAndBoardTicket(ticket.getTicketNumber(),sourceId,date,"staff@test.invalid").getValid());assertFalse(gate.validateAndBoardTicket(ticket.getTicketNumber(),sourceId,date,"staff@test.invalid").getValid());assertTrue(tickets.findById(ticket.getId()).orElseThrow().getIsBoarded());var after=dashboard.getDashboardStats();assertEquals(before.getBoardedPassengers()+2,after.getBoardedPassengers());assertEquals(before.getBoardedTickets()+1,after.getBoardedTickets());assertEquals(2,scanLogs.findByTicketId(ticket.getId()).size());
    }
    @Test void concurrentScansCommitExactlyOneAdmission()throws Exception{
      var t=boardable(1);var workers=Executors.newFixedThreadPool(2);var firstWritten=new CountDownLatch(1);var commit=new CountDownLatch(1);
      try{Future<?> first=workers.submit(()->tx().executeWithoutResult(s->{assertTrue(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff A").getValid());firstWritten.countDown();await(commit);}));assertTrue(firstWritten.await(10,TimeUnit.SECONDS));Future<?> second=workers.submit(()->assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff B").getValid()));assertThrows(TimeoutException.class,()->second.get(250,TimeUnit.MILLISECONDS));commit.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);assertEquals(1,scanLogs.findByTicketId(t.getId()).stream().filter(l->l.getScanResult()==com.trainbooking.it25100228.model.BoardingLog.ScanResult.VALID).count());
      }finally{commit.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void wrongServiceDateAndCancelledDepartureCannotBoard(){var t=boardable(1);assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),targetId,date,"staff").getValid());assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date.plusDays(1),"staff").getValid());statuses.update(change("CANCELLED",0),"coordinator");assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff").getValid());assertFalse(tickets.findById(t.getId()).orElseThrow().getIsBoarded());assertEquals(3,scanLogs.findByTicketId(t.getId()).size());}
    @Test void unpaidAndInvalidAllocationAreRecordedRejections(){var t=boardable(1);tx().executeWithoutResult(s->em.find(com.trainbooking.it25100977.model.Payment.class,t.getPayment().getId()).setStatus(com.trainbooking.it25100977.model.Payment.PaymentStatus.FAILED));assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff").getValid());tx().executeWithoutResult(s->{em.find(com.trainbooking.it25100977.model.Payment.class,t.getPayment().getId()).setStatus(com.trainbooking.it25100977.model.Payment.PaymentStatus.COMPLETED);em.find(Booking.class,t.getBooking().getId()).setSeatNumbers("Car 99 / S99");});assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff").getValid());assertFalse(tickets.findById(t.getId()).orElseThrow().getIsBoarded());}
    @Test void futureTicketAndUnknownMalformedReferencesCannotBeAdmitted(){var b=paid("Car 01 / S03",1);var t=tickets.findByBookingId(b.getId()).orElseThrow();assertFalse(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff").getValid());assertFalse(gate.validateAndBoardTicket("TKT-UNKNOWN-999",sourceId,date,"staff").getValid());assertFalse(gate.validateAndBoardTicket("123",sourceId,date,"staff").getValid());assertFalse(tickets.findById(t.getId()).orElseThrow().getIsBoarded());}
    @Test void failedOuterTransactionCannotMarkTicketBoarded(){var t=boardable(1);tx().executeWithoutResult(s->{assertTrue(gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff").getValid());s.setRollbackOnly();});assertFalse(tickets.findById(t.getId()).orElseThrow().getIsBoarded());assertTrue(scanLogs.findByTicketId(t.getId()).isEmpty());}
    @Test void correctionAndArchiveRetainHistoryAndAdjustCurrentBoardedCounts(){var before=dashboard.getDashboardStats();var t=boardable(2);gate.validateAndBoardTicket(t.getTicketNumber(),sourceId,date,"staff");assertThrows(IllegalArgumentException.class,()->gate.correctBoarding(t.getId(),"", "admin"));gate.correctBoarding(t.getId(),"Wrong admission recorded", "admin");assertFalse(tickets.findById(t.getId()).orElseThrow().getIsBoarded());assertEquals(before.getBoardedPassengers(),dashboard.getDashboardStats().getBoardedPassengers());var events=scanLogs.findByTicketId(t.getId());assertEquals(2,events.size());var id=events.get(0).getId();gate.archiveLog(id,true,"admin");assertTrue(scanLogs.findById(id).orElseThrow().getArchived());gate.archiveLog(id,false,"admin");assertFalse(scanLogs.findById(id).orElseThrow().getArchived());assertEquals(2,scanLogs.findByTicketId(t.getId()).size());}
    @Test void staffAccessIsAuditedAndSessionsExpireOnlyAfterCommit(){String admin="admin-"+UUID.randomUUID()+"@test.invalid";var operator=users.save(User.builder().email(admin).firstName("Demo").lastName("Admin").password("unchanged-hash").role(User.Role.ADMIN).build());String hash=users.findById(userId).orElseThrow().getPassword();long before=roleAudit.count();String session="session-"+UUID.randomUUID();registry.registerNewSession(session,org.springframework.security.core.userdetails.User.withUsername(owner).password("hash").roles("PASSENGER").build());
        tx().executeWithoutResult(s->{access.change(userId,"STATION_STAFF","Assigned to station gate",admin);assertFalse(registry.getSessionInformation(session).isExpired());});assertEquals(User.Role.STATION_STAFF,users.findById(userId).orElseThrow().getRole());assertEquals(hash,users.findById(userId).orElseThrow().getPassword());assertEquals(before+1,roleAudit.count());assertTrue(registry.getSessionInformation(session).isExpired());
        assertThrows(IllegalStateException.class,()->access.change(operator.getId(),"PASSENGER","Do not remove admin",admin));assertThrows(IllegalArgumentException.class,()->access.change(otherId,"ADMIN","Invalid promotion",admin));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.change(otherId,"COORDINATOR","Not authorized",owner));
        String otherSession="session-"+UUID.randomUUID();registry.registerNewSession(otherSession,org.springframework.security.core.userdetails.User.withUsername(other).password("hash").roles("PASSENGER").build());tx().executeWithoutResult(s->{access.change(otherId,"COORDINATOR","Rolled back update",admin);s.setRollbackOnly();});assertEquals(User.Role.PASSENGER,users.findById(otherId).orElseThrow().getRole());assertEquals(before+1,roleAudit.count());assertFalse(registry.getSessionInformation(otherSession).isExpired());
    }
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
