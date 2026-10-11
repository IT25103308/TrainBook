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
@SpringJUnitConfig(DisruptionPersistenceTest.PersistenceOnly.class)
class DisruptionPersistenceTest {
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={UserRepository.class,ScheduleRepository.class,BookingRepository.class,PaymentRepository.class,NotificationRepository.class})
    @Import({BookingService.class,PaymentService.class,CatalogWriteLock.class,ServiceStatusService.class,ServiceChangeSubject.class,ServiceAuditObserver.class,
        PassengerNotificationObserver.class,RebookingService.class,NotificationService.class,TrainLocationService.class})
    static class PersistenceOnly {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:uc05_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.trainbooking");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean EmailService email(){return mock(EmailService.class);}
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

    @Test void rollbackEmitsNoAlertsOrAuditAndLeavesTheDepartureBookable(){
        paid("Car 01 / S03",1);long before=audit.count();tx().executeWithoutResult(s->{statuses.update(change("CANCELLED",0),"coordinator");s.setRollbackOnly();});
        assertTrue(disruptions.findByScheduleIdAndServiceDate(sourceId,date).isEmpty());assertEquals(before,audit.count());assertTrue(notifications.findOwned(userId).isEmpty());assertTrue(tokens.findAll().stream().noneMatch(t->t.getSourceScheduleId()!=null && t.getSourceScheduleId().equals(sourceId)));
    }
    @Test void committedStatusIsDatedAndOnlyPaidUnboardedTicketsReceiveOffers(){
        Booking paid=paid("Car 01 / S03",1);bookingService.createBooking(request(sourceId,date,1,"Car 01 / S04"),otherId);
        bookingService.createBooking(request(sourceId,date.plusWeeks(1),1,"Car 01 / S05"),otherId);var token=cancellation(paid);
        assertEquals(sourceId,token.getSourceScheduleId());assertEquals(date,token.getSourceDate());assertEquals(1,tokens.findByBookingIdAndIsRedeemedFalse(paid.getId()).size());
        assertEquals(1,notifications.findOwned(userId).size());assertEquals(1,notifications.findOwned(otherId).size());
        assertFalse(bookingService.getCheckoutRecovery(notifications.findOwned(otherId).get(0).getBookingId()).canSelectAgain());
        assertThrows(IllegalArgumentException.class,()->bookingService.createBooking(request(sourceId,date,1,"Car 01 / S06"),otherId));
        assertNotNull(bookingService.createBooking(request(sourceId,date.plusWeeks(1),1,"Car 01 / S06"),otherId));
        assertFalse(statuses.board(date.plusWeeks(1)).stream().filter(s->s.scheduleId().equals(sourceId)).findFirst().orElseThrow().status().equals("CANCELLED"));
    }
    @Test void delayThresholdAndChannelPreferencesAreAppliedToTheActualDate(){
        tx().executeWithoutResult(s->em.find(User.class,userId).setDelayAlertThresholdMinutes(40));
        bookingService.createBooking(request(sourceId,date,1,"Car 01 / S03"),userId);bookingService.createBooking(request(sourceId,date,1,"Car 01 / S04"),otherId);
        statuses.update(change("DELAYED",30),"coordinator");assertTrue(notifications.findOwned(userId).isEmpty());assertEquals(1,notifications.findOwned(otherId).size());
        assertEquals("NOT_REQUESTED",notifications.findOwned(otherId).get(0).getEmailResult());assertFalse(notifications.findOwned(otherId).get(0).getSmsSimulated());verify(email,never()).sendSimpleEmail(any(),any(),any());
    }
    @Test void successfulTransferAllocatesFreeTargetSeatsAndRetainsPaymentTicketAndHistory(){
        Booking source=paid("Car 01 / S03, Car 01 / S07",2);var originalTicket=tickets.findByBookingId(source.getId()).orElseThrow();var originalPayment=payments.findByBookingId(source.getId()).orElseThrow();
        bookingService.createBooking(request(targetId,date,2,"Car 01 / S01, Car 01 / S02"),otherId);var token=cancellation(source);
        assertTrue(rebooking.alternatives(token.getToken(),owner).stream().anyMatch(o->o.scheduleId().equals(targetId)));
        rebooking.transfer(token.getToken(),targetId,date,owner);var stored=bookings.findById(source.getId()).orElseThrow();assertEquals(targetId,stored.getSchedule().getId());assertEquals("Car 01 / S03, Car 01 / S04",stored.getSeatNumbers());
        assertEquals(originalPayment.getAmount(),stored.getTotalAmount());assertEquals(originalPayment.getId(),payments.findByBookingId(source.getId()).orElseThrow().getId());assertEquals(originalTicket.getId(),tickets.findByBookingId(source.getId()).orElseThrow().getId());
        assertEquals(10,bookingService.getAvailableSeats(sourceId,date,"FIRST"));assertEquals(6,bookingService.getAvailableSeats(targetId,date,"FIRST"));assertEquals(1,transfers.findByBookingIdOrderByIdDesc(source.getId()).size());
        assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));
    }
    @Test void wrongOwnerDateAndFullReplacementLeaveSourceAndOfferIntact(){
        Booking source=paid("Car 01 / S03, Car 01 / S07",2);var token=cancellation(source);
        assertThrows(com.trainbooking.exception.ResourceNotFoundException.class,()->rebooking.transfer(token.getToken(),targetId,date,other));
        assertThrows(IllegalArgumentException.class,()->rebooking.transfer(token.getToken(),targetId,date.plusWeeks(1),owner));
        tx().executeWithoutResult(s->em.find(Schedule.class,targetId).getTrain().setFirstClassSeats(2));bookingService.createBooking(request(targetId,date,2,"Car 01 / S01, Car 01 / S02"),otherId);
        assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));assertEquals(sourceId,bookings.findById(source.getId()).orElseThrow().getSchedule().getId());assertFalse(tokens.findById(token.getId()).orElseThrow().getIsRedeemed());
    }
    @Test void boardedAndExpiredOffersCannotMoveAReservation(){
        Booking source=paid("Car 01 / S03",1);var token=cancellation(source);
        tx().executeWithoutResult(s->em.find(com.trainbooking.it25100977.model.Ticket.class,tickets.findByBookingId(source.getId()).orElseThrow().getId()).setIsBoarded(true));
        assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));
        tx().executeWithoutResult(s->{em.find(com.trainbooking.it25100977.model.Ticket.class,tickets.findByBookingId(source.getId()).orElseThrow().getId()).setIsBoarded(false);em.find(RebookingToken.class,token.getId()).setExpiresAt(LocalDateTime.now().minusSeconds(1));});
        assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));assertEquals(sourceId,bookings.findById(source.getId()).orElseThrow().getSchedule().getId());
    }
    @Test void restoringServiceInvalidatesEligibilityWithoutErasingTheOldOffer(){
        Booking source=paid("Car 01 / S03",1);var token=cancellation(source);statuses.update(change("ON_TIME",0),"coordinator");
        assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));assertTrue(tokens.existsById(token.getId()));assertNotNull(bookingService.createBooking(request(sourceId,date,1,"Car 01 / S04"),otherId));
    }
    @Test void twoConcurrentClaimsOfOneOfferProduceOneTransfer() throws Exception {
        Booking source=paid("Car 01 / S03",1);var token=cancellation(source);var workers=Executors.newFixedThreadPool(2);var saved=new CountDownLatch(1);var commit=new CountDownLatch(1);
        try {Future<?> first=workers.submit(()->tx().executeWithoutResult(s->{rebooking.transfer(token.getToken(),targetId,date,owner);saved.countDown();await(commit);}));assertTrue(saved.await(10,TimeUnit.SECONDS));
            Future<?> second=workers.submit(()->assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner)));assertThrows(TimeoutException.class,()->second.get(250,TimeUnit.MILLISECONDS));commit.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(1,transfers.findByBookingIdOrderByIdDesc(source.getId()).size());assertEquals(1,tickets.findAllByBookingId(source.getId()).size());assertEquals(1,payments.findAllByBookingId(source.getId()).size());
        } finally {commit.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void notificationReadArchiveRestoreAreOwnedAndPersisted(){
        Booking source=paid("Car 01 / S03",1);cancellation(source);var row=notifications.findOwned(userId).get(0);
        assertThrows(com.trainbooking.exception.ResourceNotFoundException.class,()->alerts.archive(row.getId(),otherId,true));alerts.markRead(row.getId(),userId);alerts.archive(row.getId(),userId,true);
        assertTrue(alerts.list(userId,"ALL").isEmpty());assertEquals(1,alerts.list(userId,"ARCHIVED").size());assertTrue(notifications.findById(row.getId()).orElseThrow().getIsRead());alerts.archive(row.getId(),userId,false);assertEquals(1,alerts.list(userId,"ALL").size());
    }
    @Test void unusedDatedStatusCanBeDeletedWhileBookedHistoryIsRetained(){
        statuses.update(change("DELAYED",20),"coordinator");Long id=disruptions.findByScheduleIdAndServiceDate(sourceId,date).orElseThrow().getId();statuses.deleteUnused(id,"coordinator");assertFalse(disruptions.existsById(id));
        paid("Car 01 / S03",1);statuses.update(change("DELAYED",20),"coordinator");Long booked=disruptions.findByScheduleIdAndServiceDate(sourceId,date).orElseThrow().getId();assertThrows(IllegalStateException.class,()->statuses.deleteUnused(booked,"coordinator"));
    }
    @Test void twoDifferentPaidOffersCannotBothTakeTheLastReplacementSeat()throws Exception{
        Booking first=paid("Car 01 / S03",1),second=paid("Car 01 / S04",1);var firstToken=cancellation(first);var secondToken=tokens.findByBookingIdAndIsRedeemedFalse(second.getId()).get(0);
        tx().executeWithoutResult(s->em.find(Schedule.class,targetId).getTrain().setFirstClassSeats(1));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var tasks=List.of(pool.submit(()->{await(start);try{rebooking.transfer(firstToken.getToken(),targetId,date,owner);return true;}catch(IllegalStateException unavailable){return false;}}),pool.submit(()->{await(start);try{rebooking.transfer(secondToken.getToken(),targetId,date,owner);return true;}catch(IllegalStateException unavailable){return false;}}));start.countDown();int won=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))won++;assertEquals(1,won);assertEquals(0,bookingService.getAvailableSeats(targetId,date,"FIRST"));
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void unrelatedRouteAndLegacyOfferCannotTransferPaidSeats(){
        Booking source=paid("Car 01 / S03",1);var token=cancellation(source);
        tx().executeWithoutResult(s->{var route=Route.builder().origin("Unrelated origin").destination("Unrelated destination").distanceKm(80).build();em.persist(route);em.find(Schedule.class,targetId).setRoute(route);});
        assertThrows(IllegalArgumentException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));
        tx().executeWithoutResult(s->em.find(RebookingToken.class,token.getId()).setSourceScheduleId(null));assertThrows(IllegalStateException.class,()->rebooking.transfer(token.getToken(),targetId,date,owner));assertEquals(sourceId,bookings.findById(source.getId()).orElseThrow().getSchedule().getId());
    }
    @Test void rolledBackTelemetryCannotAppearAsACommittedPosition(){
        Long trainId=schedules.findById(sourceId).orElseThrow().getTrain().getId();
        tx().executeWithoutResult(s->{locations.report(trainId,null,null,7.0,80.0,20.0,"ON_TIME",null,null,"OUTBOUND",null,10,0,null,true,"demo operator");s.setRollbackOnly();});assertTrue(locations.getLocation(trainId).isEmpty());
        locations.report(trainId,null,null,7.0,80.0,20.0,"ON_TIME",null,null,"OUTBOUND",null,10,0,null,true,"demo operator");assertTrue(locations.getLocation(trainId).orElseThrow().getSimulated());locations.clear(trainId);assertTrue(locations.getLocation(trainId).isEmpty());
    }
    static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
