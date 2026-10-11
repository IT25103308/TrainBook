package com.trainbooking.it25103308;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Real transactions and repositories against a disposable, in-memory H2 database.
 * This does not start the application, read its datasource settings, enable its
 * scheduled jobs, or exercise MariaDB/MySQL's particular locking behavior.
 */
@SpringJUnitConfig(BookingPersistenceTest.PersistenceOnlyConfig.class)
class BookingPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {
            UserRepository.class, ScheduleRepository.class, BookingRepository.class, PaymentRepository.class
    })
    @Import({BookingService.class, PaymentService.class,com.trainbooking.it25102327.service.TrainService.class})
    static class PersistenceOnlyConfig {
        @Bean com.trainbooking.it25102327.service.ScheduleService scheduleService(){return mock(com.trainbooking.it25102327.service.ScheduleService.class);}
        @Bean
        DataSource dataSource() {
            // The URL is fixed to the in-memory scheme; external properties cannot replace it.
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:uc03_" + UUID.randomUUID()
                            + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(User.class.getPackageName(), Schedule.class.getPackageName(),
                    Booking.class.getPackageName(), Payment.class.getPackageName(),"com.trainbooking.it25102925.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.dialect", "org.hibernate.dialect.H2Dialect",
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.show_sql", "false"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        EmailService emailService() {
            return mock(EmailService.class);
        }
    }

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private com.trainbooking.it25100977.repository.TicketRepository ticketRepository;
    @Autowired private EmailService emailService;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private com.trainbooking.it25102327.repository.RouteRepository routeRepository;
    @Autowired private com.trainbooking.it25102327.repository.TrainRepository trainRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.trainbooking.it25102327.service.TrainService managedSearch;
    @PersistenceContext private EntityManager entityManager;

    private LocalDate travelDate;
    private Long scheduleId;
    private Long firstPassengerId;
    private Long secondPassengerId;

    private com.trainbooking.it25102327.service.TrainService searchService() {
        return managedSearch;
    }

    @Test
    void searchUsesCommittedHeldSeatsAndRefreshesAfterRelease() {
        Booking held = bookingService.createBooking(request("FIRST", 2, "Car 01 / S03, Car 01 / S04"), firstPassengerId);
        var search = searchService();
        var result = search.searchTrains("Test origin", "Test destination", travelDate, "FIRST").stream()
                .filter(row -> row.getScheduleId().equals(scheduleId)).findFirst().orElseThrow();
        assertEquals(8, result.getAvailableFirstClassSeats());
        assertEquals("LKR 1,500.00", result.getFormattedFare());
        bookingService.releaseLock(held.getId());
        var refreshed = search.searchTrains("Test origin", "Test destination", travelDate, "FIRST").stream()
                .filter(row -> row.getScheduleId().equals(scheduleId)).findFirst().orElseThrow();
        assertEquals(10, refreshed.getAvailableFirstClassSeats());
    }

    @Test
    void actualScheduleQueryRequiresExactStationsAndTheCorrectWeekday() {
        assertTrue(scheduleRepository.findMatchingSchedules("test origin", "TEST DESTINATION", travelDate.getDayOfWeek())
                .stream().anyMatch(schedule -> schedule.getId().equals(scheduleId)));
        assertFalse(scheduleRepository.findMatchingSchedules("Test", "Test destination", travelDate.getDayOfWeek())
                .stream().anyMatch(schedule -> schedule.getId().equals(scheduleId)));
        assertFalse(scheduleRepository.findMatchingSchedules("Test origin", "Test destination", travelDate.getDayOfWeek().plus(1))
                .stream().anyMatch(schedule -> schedule.getId().equals(scheduleId)));
    }

    @Test
    void newlySavedRouteStationsAppearWithoutAnApplicationRestart() {
        String newStation = "New test station " + java.util.UUID.randomUUID();
        var search = searchService();
        assertFalse(search.getStations().contains(newStation));
        transaction().executeWithoutResult(status -> entityManager.persist(Route.builder()
                .origin(newStation).destination("Test destination").distanceKm(10).build()));
        assertTrue(search.getStations().contains(newStation));
    }

    @BeforeEach
    void createIsolatedFixtures() {
        org.mockito.Mockito.reset(emailService);
        travelDate = LocalDate.now().plusDays(1);
        transaction().executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            User first = passenger("first-" + suffix + "@example.test");
            User second = passenger("second-" + suffix + "@example.test");
            entityManager.persist(first);
            entityManager.persist(second);
            Train train = Train.builder().trainNumber("TEST-" + suffix)
                    .trainName("Persistence test train").firstClassSeats(10).secondClassSeats(20)
                    .totalSeats(30).status(Train.TrainStatus.ACTIVE).build();
            entityManager.persist(train);
            Route route = Route.builder().origin("Test origin").destination("Test destination")
                    .train(train).build();
            entityManager.persist(route);
            Schedule schedule = Schedule.builder().train(train).route(route)
                    .dayOfWeek(travelDate.getDayOfWeek())
                    .departureTime(LocalTime.of(10, 0)).arrivalTime(LocalTime.of(12, 0))
                    .firstClassFare(new BigDecimal("1500.00"))
                    .secondClassFare(new BigDecimal("800.00")).build();
            entityManager.persist(schedule);
            entityManager.flush();
            firstPassengerId = first.getId();
            secondPassengerId = second.getId();
            scheduleId = schedule.getId();
        });
    }

    @Test
    void selectedSeatsAndServerFareSurviveCommitAndReload() {
        Booking result = bookingService.createBooking(request("SECOND", 2, "Car 02 / S03, Car 02 / S04"), firstPassengerId);

        // A separate transaction reloads the committed row; no mocked repository or shared entity cache.
        transaction().executeWithoutResult(status -> {
            Booking stored = entityManager.find(Booking.class, result.getId());
            assertEquals("Car 02 / S03, Car 02 / S04", stored.getSeatNumbers());
            assertEquals(2, stored.getNumberOfSeats());
            assertEquals(new BigDecimal("1600.00"), stored.getTotalAmount());
            assertEquals(Booking.SeatClass.SECOND, stored.getSeatClass());
            assertEquals(Booking.BookingStatus.PENDING, stored.getStatus());
            assertEquals(firstPassengerId, stored.getPassenger().getId());
            assertNotNull(stored.getLockExpiresAt());
        });
    }

    @Test
    void overlappingRequestsForSameSeatProduceOnlyOneCommittedReservation() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstSavedButNotCommitted = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            // The service joins this READ_COMMITTED transaction. Keep it open after saving
            // so the second passenger definitely competes with an uncommitted reservation.
            Future<Long> winner = workers.submit(() -> transaction().execute(status -> {
                Booking booking = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
                firstSavedButNotCommitted.countDown();
                await(allowFirstCommit);
                return booking.getId();
            }));
            assertTrue(firstSavedButNotCommitted.await(10, TimeUnit.SECONDS), "First reservation did not reach save");
            Future<IllegalStateException> loser = workers.submit(() -> {
                secondStarted.countDown();
                return assertThrows(IllegalStateException.class,
                        () -> bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), secondPassengerId));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS), "Second passenger did not start");
            assertThrows(TimeoutException.class, () -> loser.get(250, TimeUnit.MILLISECONDS),
                    "The competing reservation must wait for the first transaction's schedule lock");
            allowFirstCommit.countDown();

            Long winningId = winner.get(10, TimeUnit.SECONDS);
            IllegalStateException rejection = loser.get(10, TimeUnit.SECONDS);
            assertTrue(rejection.getMessage().contains("already held or booked"), rejection.getMessage());
            List<Booking> stored = bookingRepository.findByScheduleId(scheduleId);
            assertEquals(1, stored.size(), "Only one passenger may own this seat");
            assertEquals(winningId, stored.get(0).getId());
            assertEquals("Car 01 / S03", stored.get(0).getSeatNumbers());
            assertEquals(Booking.BookingStatus.PENDING, stored.get(0).getStatus());
        } finally {
            allowFirstCommit.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Reservation workers did not finish");
        }
    }

    @Test
    void expiredPaymentIsRejectedAndCleanupActuallyReleasesTheSeats() {
        Booking booking = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
        transaction().executeWithoutResult(status -> entityManager.find(Booking.class, booking.getId())
                .setLockExpiresAt(LocalDateTime.now().minusSeconds(1)));
        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayment(booking.getId(), new PaymentRequest()));
        assertTrue(paymentRepository.findAllByBookingId(booking.getId()).isEmpty());
        bookingService.releaseExpiredLocks();
        assertEquals(Booking.BookingStatus.CANCELLED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED,
                bookingRepository.findById(booking.getId()).orElseThrow().getCancellationReason());
        assertEquals(10, bookingService.getAvailableSeats(scheduleId, travelDate, "FIRST"));
    }

    @Test
    void competingPaymentSubmitsCommitOnlyOnePaymentAndConfirmedBooking() throws Exception {
        Booking booking = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<Long> first = workers.submit(() -> transaction().execute(status -> {
                Payment payment = paymentService.processPayment(booking.getId(), validPaymentRequest());
                firstSaved.countDown();
                await(allowCommit);
                return payment.getId();
            }));
            assertTrue(firstSaved.await(10, TimeUnit.SECONDS));
            Future<Long> second = workers.submit(() -> {
                secondStarted.countDown();
                return paymentService.processPayment(booking.getId(), validPaymentRequest()).getId();
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, paymentRepository.findAllByBookingId(booking.getId()).size());
            assertEquals(Booking.BookingStatus.CONFIRMED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void paymentWaitingBehindCancellationCannotConfirmOrChargeCancelledReservation() throws Exception {
        Booking booking = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch cancelledButNotCommitted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch paymentStarted = new CountDownLatch(1);
        try {
            Future<?> cancel = workers.submit(() -> transaction().executeWithoutResult(status -> {
                bookingService.releaseLock(booking.getId());
                cancelledButNotCommitted.countDown();
                await(allowCommit);
            }));
            assertTrue(cancelledButNotCommitted.await(10, TimeUnit.SECONDS));
            Future<IllegalStateException> pay = workers.submit(() -> {
                paymentStarted.countDown();
                return assertThrows(IllegalStateException.class,
                        () -> paymentService.processPayment(booking.getId(), new PaymentRequest()));
            });
            assertTrue(paymentStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pay.get(250, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            cancel.get(10, TimeUnit.SECONDS);
            assertTrue(pay.get(10, TimeUnit.SECONDS).getMessage().contains("cancelled"));
            assertTrue(paymentRepository.findAllByBookingId(booking.getId()).isEmpty());
            assertEquals(Booking.BookingStatus.CANCELLED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void invalidCvvAndDeclineAllowRetryWithoutChangingHoldAndConfirmedSeatsStayBooked() {
        Booking booking = bookingService.createBooking(request("FIRST", 2, "Car 01 / S03, Car 01 / S07"), firstPassengerId);
        LocalDateTime originalExpiry = bookingRepository.findById(booking.getId()).orElseThrow().getLockExpiresAt();
        PaymentRequest paymentRequest = validPaymentRequest();
        paymentRequest.setCvv("0000");
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(booking.getId(), paymentRequest));
        assertTrue(invalid.getMessage().contains("exactly 3 digits"));
        assertTrue(paymentRepository.findAllByBookingId(booking.getId()).isEmpty());
        assertEquals(Booking.BookingStatus.PENDING, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(originalExpiry, bookingRepository.findById(booking.getId()).orElseThrow().getLockExpiresAt());

        paymentRequest.setCvv("000");
        IllegalArgumentException declined = assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(booking.getId(), paymentRequest));
        assertTrue(declined.getMessage().contains("declined"));
        assertEquals(1,paymentRepository.findAllByBookingId(booking.getId()).size());
        assertEquals(Payment.PaymentStatus.FAILED,paymentRepository.findByBookingId(booking.getId()).orElseThrow().getStatus());
        assertEquals(8, bookingService.getAvailableSeats(scheduleId, travelDate, "FIRST"));
        assertEquals(originalExpiry, bookingRepository.findById(booking.getId()).orElseThrow().getLockExpiresAt());

        paymentRequest.setCvv("123");
        Payment paid = paymentService.processPayment(booking.getId(), paymentRequest);
        assertEquals(Payment.PaymentStatus.COMPLETED, paid.getStatus());
        assertEquals(new BigDecimal("3000.00"), paid.getAmount());
        assertEquals(1, paymentRepository.findAllByBookingId(booking.getId()).size());
        assertEquals(Booking.BookingStatus.CONFIRMED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(originalExpiry, bookingRepository.findById(booking.getId()).orElseThrow().getLockExpiresAt());
        assertEquals("Car 01 / S03, Car 01 / S07", paymentService.generateTicket(booking.getId()).getSeatNumbers());

        transaction().executeWithoutResult(status -> entityManager.find(Booking.class, booking.getId())
                .setLockExpiresAt(LocalDateTime.now().minusSeconds(1)));
        bookingService.releaseExpiredLocks();
        assertEquals(Booking.BookingStatus.CONFIRMED, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        assertEquals(8, bookingService.getAvailableSeats(scheduleId, travelDate, "FIRST"));
    }

    private PaymentRequest validPaymentRequest() {
        return PaymentRequest.builder().cardHolderName("Test Passenger")
                .cardNumber("4111222233334444").cvv("123").expiryMonth("12")
                .expiryYear(String.valueOf(LocalDate.now().getYear() + 1)).build();
    }

    @Test
    void cancellationReasonsPersistAndLegacyUnknownReasonsStayCompatible() {
        Booking expired = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
        transaction().executeWithoutResult(status -> entityManager.find(Booking.class, expired.getId())
                .setLockExpiresAt(LocalDateTime.now().minusSeconds(1)));
        bookingService.releaseExpiredLocks();
        Booking expiredStored = bookingRepository.findById(expired.getId()).orElseThrow();
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED, expiredStored.getCancellationReason());
        assertEquals("EXPIRED_RELEASED", com.trainbooking.it25103308.dto.CheckoutHold.from(expiredStored, LocalDateTime.now()).state());
        assertTrue(bookingService.getCheckoutRecovery(expired.getId()).canSelectAgain(),
                "Recovery reads lazy journey data inside its transaction");

        Booking manual = bookingService.createBooking(request("FIRST", 1, "Car 01 / S07"), secondPassengerId);
        LocalDateTime originalExpiry = bookingRepository.findById(manual.getId()).orElseThrow().getLockExpiresAt();
        bookingService.releaseLock(manual.getId());
        Booking manualStored = bookingRepository.findById(manual.getId()).orElseThrow();
        assertEquals(Booking.CancellationReason.PASSENGER, manualStored.getCancellationReason());
        assertEquals(originalExpiry, manualStored.getLockExpiresAt());

        Booking legacy = bookingService.createBooking(request("FIRST", 1, "Car 01 / S01"), firstPassengerId);
        transaction().executeWithoutResult(status -> entityManager.find(Booking.class, legacy.getId())
                .setStatus(Booking.BookingStatus.CANCELLED));
        Booking legacyStored = bookingRepository.findById(legacy.getId()).orElseThrow();
        assertNull(legacyStored.getCancellationReason());
        assertEquals("CANCELLED", com.trainbooking.it25103308.dto.CheckoutHold.from(legacyStored, LocalDateTime.now()).state());
        assertEquals(10, bookingService.getAvailableSeats(scheduleId, travelDate, "FIRST"));
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    @Test void declinedAttemptPersistsAndRetryUpdatesTheSameRecordWithActualPaidTime() {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        PaymentRequest request=validPaymentRequest();request.setCvv("000");
        assertThrows(com.trainbooking.it25100977.service.PaymentDeclinedException.class,()->paymentService.payAndIssue(held.getId(),request));
        Payment failed=paymentRepository.findByBookingId(held.getId()).orElseThrow();assertEquals(Payment.PaymentStatus.FAILED,failed.getStatus());
        assertNull(failed.getPaidAt());assertNotNull(failed.getAttemptedAt());assertTrue(ticketRepository.findByBookingId(held.getId()).isEmpty());
        request.setCvv("123");var ticket=paymentService.payAndIssue(held.getId(),request);
        Payment completed=paymentRepository.findByBookingId(held.getId()).orElseThrow();assertEquals(failed.getId(),completed.getId());
        assertEquals(Payment.PaymentStatus.COMPLETED,completed.getStatus());assertNotNull(completed.getPaidAt());assertNotNull(ticket.getId());
        assertEquals(1,paymentRepository.findAllByBookingId(held.getId()).size());
    }

    @Test void cancellingOrExpiringAnUnpaidIntentRetainsACancelledPaymentOutcome() {
        for(boolean expire:new boolean[]{false,true}) {
            Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
            PaymentRequest request=validPaymentRequest();request.setCvv("000");
            assertThrows(com.trainbooking.it25100977.service.PaymentDeclinedException.class,()->paymentService.payAndIssue(held.getId(),request));
            Long paymentId=paymentRepository.findByBookingId(held.getId()).orElseThrow().getId();
            if(expire) {transaction().executeWithoutResult(s->entityManager.find(Booking.class,held.getId()).setLockExpiresAt(LocalDateTime.now().minusSeconds(1)));bookingService.releaseExpiredLocks();}
            else bookingService.cancelPendingBooking(held.getId(),ownerEmail());
            var payment=paymentRepository.findByBookingId(held.getId()).orElseThrow();assertEquals(paymentId,payment.getId());assertEquals(Payment.PaymentStatus.CANCELLED,payment.getStatus());
            assertNull(payment.getPaidAt());assertEquals(10,bookingService.getAvailableSeats(scheduleId,travelDate,"FIRST"));
        }
    }

    @Test void ticketPersistenceFailureRollsBackPaymentAndConfirmationTogether() {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        Booking other=bookingService.createBooking(request("FIRST",1,"Car 01 / S07"),firstPassengerId);
        var conflict=paymentService.payAndIssue(other.getId(),validPaymentRequest());
        transaction().executeWithoutResult(s->entityManager.find(com.trainbooking.it25100977.model.Ticket.class,conflict.getId())
            .setTicketNumber("TKT-"+java.time.Year.now().getValue()+"-"+(10000+held.getId())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->paymentService.payAndIssue(held.getId(),validPaymentRequest()));
        assertEquals(Booking.BookingStatus.PENDING,bookingRepository.findById(held.getId()).orElseThrow().getStatus());
        assertTrue(paymentRepository.findAllByBookingId(held.getId()).isEmpty());assertTrue(ticketRepository.findByBookingId(held.getId()).isEmpty());
    }

    @Test void failedEmailAfterCommitDoesNotUndoPaidTicketAndRepeatedSubmissionDoesNotResend() {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        org.mockito.Mockito.when(emailService.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(emailService.sendEmailWithAttachment(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenReturn(EmailService.DeliveryResult.FAILED);
        Long id=transaction().execute(s->{var ticket=paymentService.payAndIssue(held.getId(),validPaymentRequest());
            org.mockito.Mockito.verify(emailService,org.mockito.Mockito.never()).sendEmailWithAttachment(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());return ticket.getId();});
        var stored=ticketRepository.findById(id).orElseThrow();assertEquals("FAILED",stored.getEmailDeliveryStatus());
        assertEquals(Booking.BookingStatus.CONFIRMED,bookingRepository.findById(held.getId()).orElseThrow().getStatus());
        assertEquals(id,paymentService.payAndIssue(held.getId(),validPaymentRequest()).getId());
        assertEquals(1,paymentRepository.findAllByBookingId(held.getId()).size());assertEquals(1,ticketRepository.findAllByBookingId(held.getId()).size());
        org.mockito.Mockito.verify(emailService).sendEmailWithAttachment(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test void competingAtomicCheckoutsCommitOnePaymentAndOneTicket() throws Exception {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        var workers=Executors.newFixedThreadPool(2);var saved=new CountDownLatch(1);var commit=new CountDownLatch(1);
        try {
            Future<Long> first=workers.submit(()->transaction().execute(s->{var ticket=paymentService.payAndIssue(held.getId(),validPaymentRequest());saved.countDown();await(commit);return ticket.getId();}));
            assertTrue(saved.await(10,TimeUnit.SECONDS));Future<Long> second=workers.submit(()->paymentService.payAndIssue(held.getId(),validPaymentRequest()).getId());
            assertThrows(TimeoutException.class,()->second.get(250,TimeUnit.MILLISECONDS));commit.countDown();
            assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,paymentRepository.findAllByBookingId(held.getId()).size());assertEquals(1,ticketRepository.findAllByBookingId(held.getId()).size());
        } finally {commit.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    private String ownerEmail() {
        return transaction().execute(s -> entityManager.find(User.class, firstPassengerId).getEmail());
    }

    private com.trainbooking.it25103308.dto.BookingDetailsRequest correctedDetails() {
        var form = new com.trainbooking.it25103308.dto.BookingDetailsRequest();
        form.setPassengerName("Corrected Passenger");form.setDocumentType("FOREIGN_PASSPORT");
        form.setPassengerNic("ab1234567");form.setContactPhone("+94 77 123 4567");return form;
    }

    @Test void editedDetailsPersistAndIssuedTicketUsesThemWithoutChangingAllocationOrDeadline() {
        Booking held=bookingService.createBooking(request("FIRST",2,"Car 01 / S03, Car 01 / S07"),firstPassengerId);
        LocalDateTime expiry=bookingRepository.findById(held.getId()).orElseThrow().getLockExpiresAt();
        bookingService.updatePassengerDetails(held.getId(),ownerEmail(),correctedDetails());
        Booking stored=bookingRepository.findById(held.getId()).orElseThrow();
        assertEquals("Corrected Passenger",stored.getPassengerName());assertEquals("AB1234567",stored.getPassengerNic());
        assertEquals("Passport",stored.getDocumentLabel());assertEquals("0771234567",stored.getContactPhone());
        assertEquals(expiry,stored.getLockExpiresAt());assertEquals(new BigDecimal("3000.00"),stored.getTotalAmount());
        assertEquals("Car 01 / S03, Car 01 / S07",stored.getSeatNumbers());assertEquals(8,bookingService.getAvailableSeats(scheduleId,travelDate,"FIRST"));
        paymentService.processPayment(held.getId(),validPaymentRequest());
        var ticket=paymentService.generateTicket(held.getId());assertEquals("Corrected Passenger",ticket.getPassengerName());assertEquals("Passport",ticket.getDocumentLabel());
        assertThrows(IllegalStateException.class,()->bookingService.updatePassengerDetails(held.getId(),ownerEmail(),correctedDetails()));
    }

    @Test void expiredSeatCanBeReusedBeforeCleanupAndOldHoldCannotPayOrEditIt() {
        Booking old=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        transaction().executeWithoutResult(s->entityManager.find(Booking.class,old.getId()).setLockExpiresAt(LocalDateTime.now().minusSeconds(1)));
        assertEquals(10,bookingService.getAvailableSeats(scheduleId,travelDate,"FIRST"));
        Booking fresh=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),secondPassengerId);
        assertThrows(IllegalStateException.class,()->paymentService.processPayment(old.getId(),validPaymentRequest()));
        assertThrows(IllegalStateException.class,()->bookingService.updatePassengerDetails(old.getId(),ownerEmail(),correctedDetails()));
        paymentService.processPayment(fresh.getId(),validPaymentRequest());bookingService.releaseExpiredLocks();
        assertEquals(Booking.BookingStatus.CANCELLED,bookingRepository.findById(old.getId()).orElseThrow().getStatus());
        assertEquals(Booking.BookingStatus.CONFIRMED,bookingRepository.findById(fresh.getId()).orElseThrow().getStatus());
        assertEquals(9,bookingService.getAvailableSeats(scheduleId,travelDate,"FIRST"));
    }

    @Test void pendingCancellationPreservesHistoryAndRejectsAnotherOwnersAttempt() {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        String owner=ownerEmail();LocalDateTime expiry=bookingRepository.findById(held.getId()).orElseThrow().getLockExpiresAt();
        assertThrows(com.trainbooking.exception.ResourceNotFoundException.class,()->bookingService.cancelPendingBooking(held.getId(),"other@test.invalid"));
        assertEquals(Booking.CancellationReason.PASSENGER,bookingService.cancelPendingBooking(held.getId(),owner));
        assertEquals(Booking.CancellationReason.PASSENGER,bookingService.cancelPendingBooking(held.getId(),owner));
        assertEquals(expiry,bookingRepository.findById(held.getId()).orElseThrow().getLockExpiresAt());
        assertEquals(10,bookingService.getAvailableSeats(scheduleId,travelDate,"FIRST"));
        assertTrue(paymentRepository.findAllByBookingId(held.getId()).isEmpty());
    }

    @Test void editAndCancelWaitingBehindPaidCommitCannotOverwritePaidState() throws Exception {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);
        String owner=ownerEmail();var workers=Executors.newFixedThreadPool(3);
        var saved=new CountDownLatch(1);var commit=new CountDownLatch(1);var started=new CountDownLatch(2);
        try {
            Future<?> pay=workers.submit(()->transaction().executeWithoutResult(s->{paymentService.processPayment(held.getId(),validPaymentRequest());saved.countDown();await(commit);}));
            assertTrue(saved.await(10,TimeUnit.SECONDS));
            Future<?> edit=workers.submit(()->{started.countDown();assertThrows(IllegalStateException.class,()->bookingService.updatePassengerDetails(held.getId(),owner,correctedDetails()));});
            Future<?> cancel=workers.submit(()->{started.countDown();assertThrows(IllegalStateException.class,()->bookingService.cancelPendingBooking(held.getId(),owner));});
            assertTrue(started.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->edit.get(250,TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class,()->cancel.get(250,TimeUnit.MILLISECONDS));commit.countDown();
            pay.get(10,TimeUnit.SECONDS);edit.get(10,TimeUnit.SECONDS);cancel.get(10,TimeUnit.SECONDS);
            Booking stored=bookingRepository.findById(held.getId()).orElseThrow();assertEquals(Booking.BookingStatus.CONFIRMED,stored.getStatus());
            assertEquals("Test Passenger",stored.getPassengerName());assertNull(stored.getCancellationReason());
            assertEquals(1,paymentRepository.findAllByBookingId(held.getId()).size());
        } finally {commit.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void stalePreviouslyLoadedPendingBookingCannotCancelAfterAnotherRequestPays() throws Exception {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);String owner=ownerEmail();
        var workers=Executors.newSingleThreadExecutor();var loaded=new CountDownLatch(1);var paid=new CountDownLatch(1);
        try {
            Future<?> stale=workers.submit(()->transaction().executeWithoutResult(s->{
                assertEquals(Booking.BookingStatus.PENDING,entityManager.find(Booking.class,held.getId()).getStatus());
                loaded.countDown();await(paid);assertThrows(IllegalStateException.class,()->bookingService.cancelPendingBooking(held.getId(),owner));s.setRollbackOnly();
            }));
            assertTrue(loaded.await(10,TimeUnit.SECONDS));paymentService.processPayment(held.getId(),validPaymentRequest());paid.countDown();stale.get(10,TimeUnit.SECONDS);
            assertEquals(Booking.BookingStatus.CONFIRMED,bookingRepository.findById(held.getId()).orElseThrow().getStatus());
        } finally {paid.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void paymentWaitingBehindDetailsCorrectionIssuesCorrectPassengerTicket() throws Exception {
        Booking held=bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId);String owner=ownerEmail();LocalDateTime expiry=bookingRepository.findById(held.getId()).orElseThrow().getLockExpiresAt();
        var workers=Executors.newFixedThreadPool(2);var edited=new CountDownLatch(1);var commit=new CountDownLatch(1);
        try {
            Future<?> edit=workers.submit(()->transaction().executeWithoutResult(s->{bookingService.updatePassengerDetails(held.getId(),owner,correctedDetails());edited.countDown();await(commit);}));
            assertTrue(edited.await(10,TimeUnit.SECONDS));Future<?> pay=workers.submit(()->paymentService.processPayment(held.getId(),validPaymentRequest()));
            assertThrows(TimeoutException.class,()->pay.get(250,TimeUnit.MILLISECONDS));commit.countDown();edit.get(10,TimeUnit.SECONDS);pay.get(10,TimeUnit.SECONDS);
            assertEquals("Corrected Passenger",paymentService.generateTicket(held.getId()).getPassengerName());
            assertEquals(expiry,bookingRepository.findById(held.getId()).orElseThrow().getLockExpiresAt());
        } finally {commit.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test void cachedTrainStatusCannotAllowReservationAfterCancellation() throws Exception {
        var workers=Executors.newSingleThreadExecutor();var loaded=new CountDownLatch(1);var changed=new CountDownLatch(1);
        try {
            Future<?> booking=workers.submit(()->transaction().executeWithoutResult(s->{
                Schedule schedule=entityManager.find(Schedule.class,scheduleId);assertEquals(Train.TrainStatus.ACTIVE,schedule.getTrain().getStatus());
                loaded.countDown();await(changed);assertThrows(IllegalArgumentException.class,()->bookingService.createBooking(request("FIRST",1,"Car 01 / S03"),firstPassengerId));s.setRollbackOnly();
            }));
            assertTrue(loaded.await(10,TimeUnit.SECONDS));transaction().executeWithoutResult(s->entityManager.find(Schedule.class,scheduleId).getTrain().setStatus(Train.TrainStatus.CANCELLED));
            changed.countDown();booking.get(10,TimeUnit.SECONDS);assertTrue(bookingRepository.findByScheduleId(scheduleId).isEmpty());
        } finally {changed.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    private BookingRequest request(String seatClass, int quantity, String seats) {
        return BookingRequest.builder().scheduleId(scheduleId).travelDate(travelDate)
                .seatClass(seatClass).numberOfSeats(quantity).seatNumbers(seats)
                .passengerName("Test Passenger").documentType("NIC")
                .passengerNic("200300901022").contactPhone("0771234567").build();
    }

    @Test
    void documentChoiceAndNormalizedPhoneSurviveCommitAndBookingsAreNewestFirst() {
        BookingRequest first = request("FIRST", 1, "Car 01 / S01");
        first.setDocumentType("FOREIGN_PASSPORT"); first.setPassengerNic("012345678"); first.setContactPhone("+94 77 123 4567");
        Booking oldest = bookingService.createBooking(first, firstPassengerId);
        Booking newest = bookingService.createBooking(request("FIRST", 1, "Car 01 / S03"), firstPassengerId);
        transaction().executeWithoutResult(status -> {
            entityManager.createQuery("update Booking b set b.bookedAt = :at where b.id = :id")
                    .setParameter("at", LocalDateTime.now().minusHours(2)).setParameter("id", oldest.getId()).executeUpdate();
            entityManager.clear();
            var rows = bookingRepository.findByPassengerIdOrderByBookedAtDescIdDesc(firstPassengerId);
            assertEquals(List.of(newest.getId(), oldest.getId()), rows.stream().map(Booking::getId).toList());
            Booking loaded = rows.get(1);
            assertEquals(Booking.DocumentType.FOREIGN_PASSPORT, loaded.getDocumentType());
            assertEquals("Passport", loaded.getDocumentLabel()); assertEquals("012345678", loaded.getPassengerNic());
            assertEquals("0771234567", loaded.getContactPhone());
            // A timestamp tie is broken by the newest booking ID.
            LocalDateTime tie = LocalDateTime.now().minusHours(1);
            entityManager.createQuery("update Booking b set b.bookedAt = :at where b.passenger.id = :id")
                    .setParameter("at", tie).setParameter("id", firstPassengerId).executeUpdate();
            entityManager.clear();
            assertEquals(List.of(newest.getId(), oldest.getId()), bookingRepository.findByPassengerIdOrderByBookedAtDescIdDesc(firstPassengerId).stream().map(Booking::getId).toList());
        });
    }

    private static User passenger(String email) {
        return User.builder().firstName("Test").lastName("Passenger").email(email)
                .password("test-only-unused-password").role(User.Role.PASSENGER).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the first transaction in time");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted", ex);
        }
    }
}
