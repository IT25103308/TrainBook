package com.trainbooking.it25102327;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25102925.service.EmailService;
import jakarta.persistence.EntityManagerFactory;
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
import static org.mockito.Mockito.mock;

/** Fixed disposable H2 database: real repositories, migrations-by-mapping, transactions and contention; never the app datasource. */
@SpringJUnitConfig(OperationalPersistenceTest.PersistenceOnly.class)
class OperationalPersistenceTest {
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={TrainRepository.class,UserRepository.class,BookingRepository.class,PaymentRepository.class})
    @Import({TrainService.class,ScheduleService.class,CatalogWriteLock.class,BookingService.class,PaymentService.class})
    static class PersistenceOnly {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:uc02_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan(Train.class.getPackageName(),User.class.getPackageName(),Booking.class.getPackageName(),Payment.class.getPackageName(),"com.trainbooking.it25102925.model");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean EmailService emailService(){return mock(EmailService.class);}
    }
    @Autowired TrainService trains;@Autowired ScheduleService schedules;@Autowired BookingService bookings;@Autowired PaymentService payments;
    @Autowired TrainRepository trainRepo;@Autowired RouteRepository routeRepo;@Autowired ScheduleRepository scheduleRepo;@Autowired UserRepository users;
    @Autowired BookingRepository bookingRepo;@Autowired PaymentRepository paymentRepo;@Autowired TicketRepository ticketRepo;@Autowired PlanningLockRepository planLock;
    @Autowired PlatformTransactionManager txManager;
    private String suffix;private LocalDate date;private Long userId;
    @BeforeEach void setup(){suffix=UUID.randomUUID().toString().substring(0,8);date=LocalDate.now().plusDays(2);if(!planLock.existsById(1L))planLock.saveAndFlush(new PlanningLock(1L));
        userId=users.save(User.builder().email(suffix+"@test.invalid").firstName("Test").lastName("Passenger").phone("0771234567").password("test-hash").role(User.Role.PASSENGER).build()).getId();}
    private Train train(){return trains.createTrain(TrainDto.builder().trainNumber("T1-"+suffix).trainName("Demo Express").firstClassSeats(10).secondClassSeats(20).status("ON_TIME").build());}
    private Route route(){return schedules.createRoute("Origin "+suffix,"Destination "+suffix,100,"20");}
    private ScheduleDto timetable(Train t,Route r,String policy){return ScheduleDto.builder().trainId(t.getId()).routeId(r.getId()).departureTime("10:30").arrivalTime("12:30").arrivalDayOffset(0)
        .dayOfWeek(date.getDayOfWeek().name()).firstClassFare(new BigDecimal("900.00")).secondClassFare(new BigDecimal("500.00")).farePolicy(policy).isActive(true).build();}
    private BookingRequest request(Long id,LocalDate travelDate){var r=new BookingRequest(id,travelDate,"FIRST",2);r.setSeatNumbers("Car 01 / S03, Car 01 / S07");r.setPassengerName("Test Passenger");r.setDocumentType("NIC");r.setPassengerNic("200300901022");r.setContactPhone("0771234567");return r;}
    private OverrideDto override(LocalDate serviceDate,String kind){var dto=new OverrideDto();dto.setServiceDate(serviceDate.toString());dto.setKind(kind);dto.setLabel("Holiday timetable");dto.setDepartureTime("13:00");dto.setArrivalTime("15:00");dto.setArrivalDayOffset(0);return dto;}
    private TransactionTemplate tx(){var tx=new TransactionTemplate(txManager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return tx;}

    @Test void unusedTrainRouteTimetableStopAndDatedChangeHaveRealCrud() {
        Train t=train();Route r=route();var dto=timetable(t,r,"FIXED");dto.setStops(new ArrayList<>(List.of(new StopDto("Intermediate "+suffix,"11:00","11:05",0,"3"))));
        Long id=schedules.createSchedule(dto).getId();assertEquals(1,schedules.getScheduleDto(id).getStops().size());
        dto.setDepartureTime("10:40");dto.setArrivalTime("12:40");dto.getStops().get(0).setArrivalTime("11:10");dto.getStops().get(0).setDepartureTime("11:15");schedules.updateSchedule(id,dto);
        assertEquals("10:40",schedules.getScheduleDto(id).getDepartureTime());
        schedules.saveOverride(id,null,override(date,"TIMETABLE"));Long changeId=schedules.getOverrides(id).get(0).getId();var updated=override(date,"TIMETABLE");updated.setLabel("Updated holiday");schedules.saveOverride(id,changeId,updated);
        assertEquals("Updated holiday",schedules.getOverrideDto(id,changeId).getLabel());schedules.deleteOverride(id,changeId);assertTrue(schedules.getOverrides(id).isEmpty());
        var routeDto=schedules.getRouteDto(r.getId());routeDto.setDistanceKm(110);schedules.updateRoute(r.getId(),routeDto);assertEquals(110,schedules.getRouteById(r.getId()).getDistanceKm());
        var edit=TrainDto.builder().trainNumber(t.getTrainNumber()).trainName(t.getTrainName()).firstClassSeats(12).secondClassSeats(20).status("ON_TIME").build();trains.updateTrain(t.getId(),edit);assertEquals(32,trains.getTrainById(t.getId()).getTotalSeats());
        schedules.deleteSchedule(id);assertFalse(scheduleRepo.existsById(id));schedules.deleteRoute(r.getId());trains.deleteTrain(t.getId());assertFalse(routeRepo.existsById(r.getId()));assertFalse(trainRepo.existsById(t.getId()));
    }
    @Test void searchQuotationReservationAndTicketUseTheSameDatedFareAndTimes() {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"DEMAND")).getId();
        LocalDate extra=date.plusDays(1);schedules.saveOverride(id,null,override(extra,"TIMETABLE"));
        var search=trains.searchTrains(r.getOrigin(),r.getDestination(),extra,"FIRST").stream().filter(v->v.getScheduleId().equals(id)).findFirst().orElseThrow();
        assertEquals(LocalTime.of(13,0),search.getDepartureTime());assertEquals(search.getFirstClassFare(),schedules.quote(id,extra,"FIRST").get("perSeatFare"));
        Booking held=bookings.createBooking(request(id,extra),userId);assertEquals(search.getFirstClassFare().multiply(BigDecimal.valueOf(2)),held.getTotalAmount());assertEquals("13:00",held.getDepartureTime());
        PaymentRequest card=new PaymentRequest();card.setCardHolderName("Test Passenger");card.setCardNumber("4111 2222 3333 4444");card.setCvv("123");card.setExpiryMonth("12");card.setExpiryYear(String.valueOf(YearMonth.now().plusYears(1).getYear()));
        payments.processPayment(held.getId(),card);Ticket ticket=payments.generateTicket(held.getId());assertEquals("13:00",ticket.getDepartureTime());assertEquals("15:00",ticket.getArrivalTime());assertEquals(held.getTotalAmount(),ticket.getTotalFare());
        assertTrue(schedules.saveOverride(id,null,override(extra,"MAINTENANCE")).startsWith("WARNING"));
        Long maintenanceId=schedules.getOverrides(id).stream().filter(o->o.getKind()==ScheduleOverride.Kind.MAINTENANCE).findFirst().orElseThrow().getId();
        assertTrue(trains.searchTrains(r.getOrigin(),r.getDestination(),extra,"FIRST").stream().noneMatch(v->v.getScheduleId().equals(id)));
        assertEquals("13:00",ticket.getDepartureTime());schedules.deleteOverride(id,maintenanceId);
        assertEquals(LocalTime.of(13,0),trains.searchTrains(r.getOrigin(),r.getDestination(),extra,"FIRST").stream().filter(v->v.getScheduleId().equals(id)).findFirst().orElseThrow().getDepartureTime());
        assertThrows(IllegalArgumentException.class,()->schedules.deleteSchedule(id));assertThrows(IllegalArgumentException.class,()->schedules.deleteRoute(r.getId()));assertThrows(IllegalArgumentException.class,()->trains.deleteTrain(t.getId()));
        assertEquals(Booking.BookingStatus.CONFIRMED,bookingRepo.findById(held.getId()).orElseThrow().getStatus());assertTrue(paymentRepo.findByBookingId(held.getId()).isPresent());assertTrue(ticketRepo.findByBookingId(held.getId()).isPresent());
        var next=schedules.getScheduleDto(id);next.setFarePolicy("FIXED");next.setFirstClassFare(new BigDecimal("1200.00"));next.setSecondClassFare(new BigDecimal("600.00"));schedules.updateSchedule(id,next);
        assertEquals(held.getTotalAmount(),bookingRepo.findById(held.getId()).orElseThrow().getTotalAmount());
    }
    @Test void concurrentDuplicateRouteCreationStoresExactlyOneCorridor() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {var operation=(Callable<Boolean>)()->{start.await();try{schedules.createRoute("Origin "+suffix,"Destination "+suffix,100,"1");return true;}catch(OperationValidation duplicate){return false;}};
            Future<Boolean> first=pool.submit(operation),second=pool.submit(operation);start.countDown();assertNotEquals(first.get(8,TimeUnit.SECONDS),second.get(8,TimeUnit.SECONDS));
            assertEquals(1,routeRepo.findAll().stream().filter(v->v.getOrigin().equals("Origin "+suffix)).count());
        } finally {pool.shutdownNow();}
    }
    @Test void capacityEditWaitsForReservationAndCannotRemoveItsCommittedSeat() throws Exception {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch allocated=new CountDownLatch(1),commit=new CountDownLatch(1);
        try {
            Future<Booking> held=pool.submit(()->tx().execute(status->{Booking b=bookings.createBooking(request(id,date),userId);allocated.countDown();try{commit.await(4,TimeUnit.SECONDS);}catch(InterruptedException ex){throw new RuntimeException(ex);}return b;}));
            assertTrue(allocated.await(4,TimeUnit.SECONDS));var edit=TrainDto.builder().trainNumber(t.getTrainNumber()).trainName(t.getTrainName()).firstClassSeats(6).secondClassSeats(20).status("ON_TIME").build();
            Future<?> change=pool.submit(()->trains.updateTrain(t.getId(),edit));assertThrows(TimeoutException.class,()->change.get(150,TimeUnit.MILLISECONDS));commit.countDown();assertNotNull(held.get(6,TimeUnit.SECONDS));
            ExecutionException rejected=assertThrows(ExecutionException.class,()->change.get(6,TimeUnit.SECONDS));assertInstanceOf(OperationValidation.class,rejected.getCause());assertEquals(10,trains.getTrainById(t.getId()).getFirstClassSeats());
        } finally {commit.countDown();pool.shutdownNow();}
    }
    @Test void datedMaintenanceKeepsTheBookingAndRejectsNewReservationsAndLatePayment() {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();Booking held=bookings.createBooking(request(id,date),userId);
        String message=schedules.saveOverride(id,null,override(date,"MAINTENANCE"));assertTrue(message.startsWith("WARNING"));
        assertThrows(IllegalArgumentException.class,()->bookings.createBooking(request(id,date),userId));
        assertThrows(IllegalStateException.class,()->payments.processPayment(held.getId(),new PaymentRequest()));
        assertEquals(Booking.BookingStatus.PENDING,bookingRepo.findById(held.getId()).orElseThrow().getStatus());
    }
    @Test void conflictingTimetableCreationSerializesAndStoresOnlyOneDeparture() throws Exception {
        Train firstTrain=train();Train secondTrain=trains.createTrain(TrainDto.builder().trainNumber("T2-"+suffix).trainName("Second Demo").firstClassSeats(10).secondClassSeats(20).status("ON_TIME").build());Route r=route();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Boolean> first=pool.submit(()->{start.await();try{schedules.createSchedule(timetable(firstTrain,r,"FIXED"));return true;}catch(OperationValidation conflict){return false;}});
            Future<Boolean> second=pool.submit(()->{start.await();try{schedules.createSchedule(timetable(secondTrain,r,"FIXED"));return true;}catch(OperationValidation conflict){return false;}});
            start.countDown();assertNotEquals(first.get(8,TimeUnit.SECONDS),second.get(8,TimeUnit.SECONDS));
            assertEquals(1,scheduleRepo.findAll().stream().filter(s->s.getRoute().getId().equals(r.getId())).count());
        }finally{pool.shutdownNow();}
    }
    @Test void paymentWaitsForAMaintenanceEditAndCannotConfirmAgainstStaleEligibility() throws Exception {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();Booking held=bookings.createBooking(request(id,date),userId);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch blocked=new CountDownLatch(1),commit=new CountDownLatch(1);
        try {
            Future<String> edit=pool.submit(()->tx().execute(status->{String message=schedules.saveOverride(id,null,override(date,"MAINTENANCE"));blocked.countDown();try{commit.await(4,TimeUnit.SECONDS);}catch(InterruptedException ex){throw new RuntimeException(ex);}return message;}));
            assertTrue(blocked.await(4,TimeUnit.SECONDS));PaymentRequest card=new PaymentRequest();card.setCardHolderName("Test Passenger");card.setCardNumber("4111222233334444");card.setCvv("123");card.setExpiryMonth("12");card.setExpiryYear(String.valueOf(YearMonth.now().plusYears(1).getYear()));
            Future<?> pay=pool.submit(()->payments.processPayment(held.getId(),card));assertThrows(TimeoutException.class,()->pay.get(150,TimeUnit.MILLISECONDS));commit.countDown();assertTrue(edit.get(6,TimeUnit.SECONDS).startsWith("WARNING"));
            ExecutionException rejected=assertThrows(ExecutionException.class,()->pay.get(6,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,rejected.getCause());
            assertEquals(Booking.BookingStatus.PENDING,bookingRepo.findById(held.getId()).orElseThrow().getStatus());assertTrue(paymentRepo.findByBookingId(held.getId()).isEmpty());
        } finally {commit.countDown();pool.shutdownNow();}
    }
    @Test void paymentRechecksTheOriginalDeadlineAfterWaitingForThePlanLock() throws Exception {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();Booking held=bookings.createBooking(request(id,date),userId);
        held.setLockExpiresAt(LocalDateTime.now().plusSeconds(2));bookingRepo.saveAndFlush(held);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            Future<?> planning=pool.submit(()->tx().execute(status->{scheduleRepo.findByIdForUpdate(id).orElseThrow();locked.countDown();try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException ex){throw new RuntimeException(ex);}return true;}));
            assertTrue(locked.await(2,TimeUnit.SECONDS));PaymentRequest card=new PaymentRequest();card.setCardHolderName("Test Passenger");card.setCardNumber("4111222233334444");card.setCvv("123");card.setExpiryMonth("12");card.setExpiryYear(String.valueOf(YearMonth.now().plusYears(1).getYear()));
            Future<?> pay=pool.submit(()->payments.processPayment(held.getId(),card));assertThrows(TimeoutException.class,()->pay.get(150,TimeUnit.MILLISECONDS));Thread.sleep(2100);release.countDown();planning.get(4,TimeUnit.SECONDS);
            ExecutionException expired=assertThrows(ExecutionException.class,()->pay.get(4,TimeUnit.SECONDS));assertTrue(expired.getCause().getMessage().contains("hold has expired"));assertTrue(paymentRepo.findByBookingId(held.getId()).isEmpty());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void anAlreadyLoadedBookingIsRefreshedBeforeRepeatingACommittedPayment() throws Exception {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();Booking held=bookings.createBooking(request(id,date),userId);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try { tx().execute(status->{
            Booking cached=bookingRepo.findById(held.getId()).orElseThrow();assertEquals(Booking.BookingStatus.PENDING,cached.getStatus());
            PaymentRequest card=new PaymentRequest();card.setCardHolderName("Test Passenger");card.setCardNumber("4111222233334444");card.setCvv("123");card.setExpiryMonth("12");card.setExpiryYear(String.valueOf(YearMonth.now().plusYears(1).getYear()));
            try { pool.submit(()->payments.processPayment(held.getId(),card)).get(4,TimeUnit.SECONDS); }catch(Exception failure){throw new RuntimeException(failure);}
            assertEquals(Booking.BookingStatus.PENDING,cached.getStatus(),"Fixture deliberately retains a stale entity like an earlier ownership lookup.");
            Payment repeated=payments.processPayment(held.getId(),card);assertEquals(Payment.PaymentStatus.COMPLETED,repeated.getStatus());
            assertEquals(1,paymentRepo.findAll().stream().filter(p->p.getBooking().getId().equals(held.getId())).count());return true;
        }); }finally{pool.shutdownNow();}
    }
    @Test void anAlreadyLoadedTimetableIsRefreshedBeforeCheckingMaintenanceEligibility() throws Exception {
        Train t=train();Route r=route();Long id=schedules.createSchedule(timetable(t,r,"FIXED")).getId();Booking held=bookings.createBooking(request(id,date),userId);ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            IllegalStateException blocked=assertThrows(IllegalStateException.class,()->tx().execute(status->{
                Booking cached=bookingRepo.findById(held.getId()).orElseThrow();assertTrue(cached.getSchedule().getOverrides().isEmpty());
                try {pool.submit(()->schedules.saveOverride(id,null,override(date,"MAINTENANCE"))).get(4,TimeUnit.SECONDS);}catch(Exception failure){throw new RuntimeException(failure);}
                PaymentRequest card=new PaymentRequest();card.setCardHolderName("Test Passenger");card.setCardNumber("4111222233334444");card.setCvv("123");card.setExpiryMonth("12");card.setExpiryYear(String.valueOf(YearMonth.now().plusYears(1).getYear()));
                return payments.processPayment(held.getId(),card);
            }));assertTrue(blocked.getMessage().contains("unavailable"));assertTrue(paymentRepo.findByBookingId(held.getId()).isEmpty());
        }finally{pool.shutdownNow();}
    }
}
