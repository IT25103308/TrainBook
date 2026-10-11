# TrainBook

Web-based train scheduling and booking system for **SE2030 — Software Engineering**, Sri Lanka Institute of Information Technology (SLIIT).

**Group:** 2026-Y2-S1-MLB-B9G2-10

TrainBook combines passenger accounts, train and timetable management, exact-seat reservations, simulated checkout, electronic tickets, service notifications and station boarding validation in one Spring Boot application.

## Team and modules

| Module | Member | Student ID | Source package |
| --- | --- | --- | --- |
| UC01 — Passenger accounts, profiles and favourite journeys | Dukshanth K. | IT25101520 | `it25101520` |
| UC02 — Trains, routes, timetables and fares | Balawickrama B.R.D. | IT25102327 | `it25102327` |
| UC03 — Seat reservations and capacity | Anfas M.S. | IT25103308 | `it25103308` |
| UC04 — Payment and electronic tickets | Shehara D.M.D. | IT25100977 | `it25100977` |
| UC05 — Service status, notifications and rebooking | Sovis W.M.A.V. | IT25102925 | `it25102925` |
| UC06 — Boarding validation and administrative reports | Mahanama M.N.S.T. | IT25100228 | `it25100228` |

Java packages are under `src/main/java/com/trainbooking/`. Matching page templates are under `src/main/resources/templates/`.

## Main features

- Passenger registration, sign-in, password confirmation, password recovery, profile management and favourite journeys.
- Train, route and schedule management, dated service overrides, capacity controls and configurable fare calculation.
- Station search, travel-date and class selection, exact seat allocation, ten-minute reservation holds, pending passenger-detail edits and cancellation.
- Simulated payment, retry handling, generated QR tickets and PDF documents. Card numbers and CVV values are not stored.
- Dated service status, passenger notifications, location reporting and eligible rebooking after service disruption.
- Role-controlled boarding checks, duplicate-scan protection, boarding records and administrative reports.
- Browser and server validation, ownership checks, CSRF protection, transactional updates and database locking for competing operations.

The **Strategy** pattern supports fare calculation in UC02. The **Observer** pattern connects committed service changes to follow-up processing in UC05. Spring MVC separates controllers, services, repositories and templates throughout the application.

## Technology

Java 17; Maven; Spring Boot 3.2.5; Spring MVC; Thymeleaf; Spring Security; Spring Data JPA/Hibernate; MySQL-compatible database; Lombok; iText; ZXing; JUnit and H2 test fixtures. Browser scripts use JavaScript, with Node.js checks under `src/test/js`.

## Local setup

1. Install **JDK 17** and Maven, or open `pom.xml` as a Maven project in IntelliJ IDEA. Select JDK 17 and enable annotation processing for Lombok.
2. Start MySQL/MariaDB. The checked-in local configuration expects `localhost:3307`, database `train_booking_db`, username `root`, and an empty local password. Use your own database credentials if different.
3. Create an empty database if needed:

   ```sql
   CREATE DATABASE IF NOT EXISTS train_booking_db CHARACTER SET utf8mb4;
   ```

4. Configure any overrides as environment variables in the terminal or IDE run configuration. The example below uses a **separate database** for a fresh evaluation installation:

   ```powershell
   $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3307/trainbook_evaluation?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
   $env:SPRING_DATASOURCE_USERNAME = 'root'
   $env:SPRING_DATASOURCE_PASSWORD = ''
   $env:SERVER_PORT = '8080'
   $env:TRAINBOOK_PUBLIC_BASE_URL = 'http://localhost:8080'
   mvn spring-boot:run
   ```

   `.env.example` documents supported variables; Spring Boot does not automatically load a `.env` file. If using database port 3306, update the URL accordingly. Choose an unused web port when running another application and update `TRAINBOOK_PUBLIC_BASE_URL` to match.

5. Alternatively, run `com.trainbooking.TrainBookingApplication` from the IDE. Wait for successful startup, then open `http://localhost:8080`.

Hibernate creates/updates tables from entity mappings (`spring.jpa.hibernate.ddl-auto=update`). `DataInitializer` supplies demonstration users and train data when the relevant tables are empty. An existing database retains its records; choose a separate database when an independent installation is required. No personal database export is included in this repository.

## Demonstration accounts

These accounts are created only when the user table is empty. They do not reset existing account passwords.

| Role | Email | Demo password |
| --- | --- | --- |
| Passenger | `passenger@trainbooking.lk` | `Passenger@123` |
| Administrator | `admin@trainbooking.lk` | `Admin@123` |
| Coordinator | `coordinator@trainbooking.lk` | `Coordinator@123` |
| Station staff | `staff@trainbooking.lk` | `Staff@123` |

Use the normal sign-in page. Role permissions control the available screens. These are local demonstration credentials; a deployed service requires its own credentials and deployment configuration.

## Build and tests

Compile and package without starting the application:

```powershell
mvn -DskipTests package
```

Run the isolated Java checks, including tests that create disposable H2 databases:

```powershell
mvn '-Dtest=!TrainBookingApplicationTests,!TemplateRenderingIntegrationTest' test
```

`TrainBookingApplicationTests` and `TemplateRenderingIntegrationTest` start the full application context. Run the full suite with `mvn test` only after configuring a disposable database, because application startup can initialize data and activate application components.

Run browser-script checks with Node.js:

```powershell
node --test src/test/js/*.test.cjs
```

## Project structure

```text
pom.xml                          Maven dependencies and build
src/main/java/com/trainbooking/
  TrainBookingApplication.java   Application entry point
  config/                       Application and security configuration
  security/                     Authentication integration
  validation/                   Shared passenger validation
  it25101520/ ... it25100228/     The six member modules
src/main/resources/
  application.properties        Local defaults
  templates/                    Thymeleaf pages
  static/                       CSS, JavaScript and images
src/test/java/                  Java tests
src/test/js/                    Browser-script tests
```

## Demonstration scope

Payment processing is simulated and does not charge real cards. Password-reset links appear in the application console by default. Optional email requires configured SMTP credentials and the corresponding feature settings. Location/status demonstrations do not establish a connection to Sri Lanka Railways' operational systems. Some map and externally hosted browser resources require internet access. Paid refunds and real banking settlement are outside this implementation.

This repository contains the complete application source, templates, static resources, tests and build configuration for the system described in the project report.
