package com.trainbooking.config;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds initial mock data (users, trains, routes, schedules, and sample tickets) on application startup
 * if the database tables are empty.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedTrainsAndSchedules();
        seedSampleTickets();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already present in database, skipping seeding.");
            return;
        }

        log.info("Seeding default system users...");

        User admin = User.builder()
                .firstName("System")
                .lastName("Administrator")
                .email("admin@trainbooking.lk")
                .password(passwordEncoder.encode("Admin@123"))
                .role(User.Role.ADMIN)
                .phone("+94771112233")
                .build();

        User coordinator = User.builder()
                .firstName("Train")
                .lastName("Coordinator")
                .email("coordinator@trainbooking.lk")
                .password(passwordEncoder.encode("Coordinator@123"))
                .role(User.Role.COORDINATOR)
                .phone("+94772223344")
                .build();

        User staff = User.builder()
                .firstName("Station")
                .lastName("Staff")
                .email("staff@trainbooking.lk")
                .password(passwordEncoder.encode("Staff@123"))
                .role(User.Role.STATION_STAFF)
                .phone("+94773334455")
                .build();

        User passenger = User.builder()
                .firstName("Kasun")
                .lastName("Perera")
                .email("passenger@trainbooking.lk")
                .password(passwordEncoder.encode("Passenger@123"))
                .role(User.Role.PASSENGER)
                .phone("+94774445566")
                .build();

        userRepository.saveAll(List.of(admin, coordinator, staff, passenger));
        log.info("Successfully seeded 4 default users.");
    }

    private void seedTrainsAndSchedules() {
        if (trainRepository.count() > 0) {
            log.info("Trains already present in database, skipping train & schedule seeding.");
            return;
        }

        log.info("Seeding Sri Lankan trains, routes, and schedules...");

        Train podiMenike = Train.builder()
                .trainNumber("1005")
                .trainName("Podi Menike")
                .totalSeats(300)
                .firstClassSeats(60)
                .secondClassSeats(240)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        Train udarataMenike = Train.builder()
                .trainNumber("1015")
                .trainName("Udarata Menike")
                .totalSeats(300)
                .firstClassSeats(60)
                .secondClassSeats(240)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        Train yalDevi = Train.builder()
                .trainNumber("4077")
                .trainName("Yal Devi")
                .totalSeats(400)
                .firstClassSeats(80)
                .secondClassSeats(320)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        Train ruhunuKumari = Train.builder()
                .trainNumber("8058")
                .trainName("Ruhunu Kumari")
                .totalSeats(350)
                .firstClassSeats(70)
                .secondClassSeats(280)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        Train denuwaraMenike = Train.builder()
                .trainNumber("1020")
                .trainName("Denuwara Menike")
                .totalSeats(280)
                .firstClassSeats(80)
                .secondClassSeats(200)
                .status(Train.TrainStatus.ON_TIME)
                .build();

        trainRepository.saveAll(List.of(podiMenike, udarataMenike, yalDevi, ruhunuKumari, denuwaraMenike));

        Route routeBadulla = Route.builder()
                .origin("Colombo Fort")
                .destination("Badulla")
                .distanceKm(292)
                .train(podiMenike)
                .build();

        Route routeKandy = Route.builder()
                .origin("Colombo Fort")
                .destination("Kandy")
                .distanceKm(121)
                .train(udarataMenike)
                .build();

        Route routeJaffna = Route.builder()
                .origin("Colombo Fort")
                .destination("Jaffna")
                .distanceKm(398)
                .train(yalDevi)
                .build();

        Route routeMatara = Route.builder()
                .origin("Colombo Fort")
                .destination("Matara")
                .distanceKm(160)
                .train(ruhunuKumari)
                .build();

        Route routeKandyToColombo = Route.builder()
                .origin("Kandy")
                .destination("Colombo Fort")
                .distanceKm(121)
                .train(denuwaraMenike)
                .build();

        routeRepository.saveAll(List.of(routeBadulla, routeKandy, routeJaffna, routeMatara, routeKandyToColombo));

        List<DayOfWeek> allDays = Arrays.asList(DayOfWeek.values());

        for (DayOfWeek day : allDays) {
            Schedule schPodi = Schedule.builder()
                    .train(podiMenike)
                    .route(routeBadulla)
                    .departureTime(LocalTime.of(5, 55))
                    .arrivalTime(LocalTime.of(16, 7))
                    .dayOfWeek(day)
                    .firstClassFare(new BigDecimal("2500.00"))
                    .secondClassFare(new BigDecimal("1200.00"))
                    .isActive(true)
                    .build();

            Schedule schUdarata = Schedule.builder()
                    .train(udarataMenike)
                    .route(routeKandy)
                    .departureTime(LocalTime.of(8, 30))
                    .arrivalTime(LocalTime.of(11, 5))
                    .dayOfWeek(day)
                    .firstClassFare(new BigDecimal("1500.00"))
                    .secondClassFare(new BigDecimal("800.00"))
                    .isActive(true)
                    .build();

            Schedule schYal = Schedule.builder()
                    .train(yalDevi)
                    .route(routeJaffna)
                    .departureTime(LocalTime.of(6, 35))
                    .arrivalTime(LocalTime.of(13, 20))
                    .dayOfWeek(day)
                    .firstClassFare(new BigDecimal("3000.00"))
                    .secondClassFare(new BigDecimal("1500.00"))
                    .isActive(true)
                    .build();

            Schedule schRuhunu = Schedule.builder()
                    .train(ruhunuKumari)
                    .route(routeMatara)
                    .departureTime(LocalTime.of(14, 0))
                    .arrivalTime(LocalTime.of(17, 15))
                    .dayOfWeek(day)
                    .firstClassFare(new BigDecimal("1800.00"))
                    .secondClassFare(new BigDecimal("900.00"))
                    .isActive(true)
                    .build();

            Schedule schDenuwara = Schedule.builder()
                    .train(denuwaraMenike)
                    .route(routeKandyToColombo)
                    .departureTime(LocalTime.of(16, 50))
                    .arrivalTime(LocalTime.of(19, 25))
                    .dayOfWeek(day)
                    .firstClassFare(new BigDecimal("1500.00"))
                    .secondClassFare(new BigDecimal("800.00"))
                    .isActive(true)
                    .build();

            scheduleRepository.saveAll(List.of(schPodi, schUdarata, schYal, schRuhunu, schDenuwara));
        }

        log.info("Successfully seeded trains, routes, and schedules for all 7 days.");
    }

    private void seedSampleTickets() {
        if (ticketRepository.count() > 0) {
            log.info("Tickets already present in database, skipping sample ticket seeding.");
            return;
        }

        log.info("Seeding sample confirmed tickets for station gate validation...");
        User passenger = userRepository.findByEmail("passenger@trainbooking.lk").orElse(null);
        List<Schedule> schedules = scheduleRepository.findAll();

        if (passenger != null && !schedules.isEmpty()) {
            Schedule schedule1 = schedules.get(0);
            Schedule schedule2 = schedules.size() > 1 ? schedules.get(1) : schedule1;

            // Sample Ticket 1: 1st Class AC on Schedule 1
            Booking booking1 = Booking.builder()
                    .passenger(passenger)
                    .schedule(schedule1)
                    .travelDate(LocalDate.now())
                    .seatClass(Booking.SeatClass.FIRST)
                    .numberOfSeats(2)
                    .seatNumbers("Car 01 / Seat A12, A13")
                    .passengerName("Kasun Perera")
                    .passengerNic("199512345678")
                    .contactPhone("+94774445566")
                    .status(Booking.BookingStatus.CONFIRMED)
                    .totalAmount(new BigDecimal("3000.00"))
                    .build();
            booking1 = bookingRepository.save(booking1);

            Payment payment1 = Payment.builder()
                    .booking(booking1)
                    .amount(new BigDecimal("3000.00"))
                    .status(Payment.PaymentStatus.COMPLETED)
                    .transactionRef("TXN-" + System.currentTimeMillis() + "-1")
                    .build();
            payment1 = paymentRepository.save(payment1);

            Ticket ticket1 = Ticket.builder()
                    .booking(booking1)
                    .payment(payment1)
                    .ticketNumber("TKT-2026-10001")
                    .qrCodeData("TKT-2026-10001")
                    .isBoarded(false)
                    .build();
            ticketRepository.save(ticket1);

            // Sample Ticket 2: 2nd Class on Schedule 2
            Booking booking2 = Booking.builder()
                    .passenger(passenger)
                    .schedule(schedule2)
                    .travelDate(LocalDate.now())
                    .seatClass(Booking.SeatClass.SECOND)
                    .numberOfSeats(1)
                    .seatNumbers("Car 02 / Seat B24")
                    .passengerName("Dilshan Silva")
                    .passengerNic("199823456789")
                    .contactPhone("+94778889900")
                    .status(Booking.BookingStatus.CONFIRMED)
                    .totalAmount(new BigDecimal("800.00"))
                    .build();
            booking2 = bookingRepository.save(booking2);

            Payment payment2 = Payment.builder()
                    .booking(booking2)
                    .amount(new BigDecimal("800.00"))
                    .status(Payment.PaymentStatus.COMPLETED)
                    .transactionRef("TXN-" + System.currentTimeMillis() + "-2")
                    .build();
            payment2 = paymentRepository.save(payment2);

            Ticket ticket2 = Ticket.builder()
                    .booking(booking2)
                    .payment(payment2)
                    .ticketNumber("TKT-2026-10002")
                    .qrCodeData("TKT-2026-10002")
                    .isBoarded(false)
                    .build();
            ticketRepository.save(ticket2);

            log.info("Successfully seeded 2 sample tickets: TKT-2026-10001 and TKT-2026-10002.");
        }
    }
}
