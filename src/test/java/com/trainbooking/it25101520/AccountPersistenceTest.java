package com.trainbooking.it25101520;

import com.trainbooking.it25101520.dto.*;
import com.trainbooking.it25101520.model.*;
import com.trainbooking.it25101520.repository.*;
import com.trainbooking.it25101520.service.*;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.repository.RouteRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Fixed disposable H2 datasource. No Boot application, XAMPP settings, scheduler or real email. */
@SpringJUnitConfig(AccountPersistenceTest.Isolated.class)
class AccountPersistenceTest {
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses={UserRepository.class,RouteRepository.class})
    @Import({PassengerService.class,AuthService.class,AccountSessions.class})
    static class Isolated {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:uc01_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa",""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.trainbooking");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.show_sql","false"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean org.springframework.security.core.session.SessionRegistry sessions() { return new org.springframework.security.core.session.SessionRegistryImpl(); }
        @Bean PasswordResetDelivery delivery() { return mock(PasswordResetDelivery.class); }
    }
    @Autowired UserRepository users;
    @Autowired FavoriteRouteRepository favorites;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired RouteRepository routes;
    @Autowired PassengerService passengers;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.security.core.session.SessionRegistry sessionRegistry;
    User own,other;
    @BeforeEach void setup() {
        favorites.deleteAll();tokens.deleteAll();users.deleteAll();routes.deleteAll();
        own=auth.registerUser(register("own@test.invalid"));other=auth.registerUser(register("other@test.invalid"));
        routes.save(Route.builder().origin("Colombo Fort").destination("Kandy").build());
        routes.save(Route.builder().origin("Colombo Fort").destination("Galle").build());
    }
    RegisterRequest register(String email) { return RegisterRequest.builder().firstName("Test").lastName("Passenger").email(email)
        .phone("+94771234567").password("InitialPass123").confirmPassword("InitialPass123").acceptedTerms(true).build(); }
    FavoriteRouteDto pair(String dest) { return FavoriteRouteDto.builder().origin("Colombo Fort").destination(dest).build(); }
    @Test void favoriteCreateReadEditDeleteSurviveIndependentTransactions() {
        var favorite=passengers.addFavoriteRoute(own.getId(),pair("Kandy"));assertEquals(1,passengers.getFavoriteRoutes(own.getId()).size());
        passengers.updateFavoriteRoute(favorite.getId(),own.getId(),pair("Galle"));assertEquals("Galle",favorites.findById(favorite.getId()).orElseThrow().getDestination());
        assertThrows(com.trainbooking.exception.ResourceNotFoundException.class,()->passengers.removeFavoriteRoute(favorite.getId(),other.getId()));
        passengers.removeFavoriteRoute(favorite.getId(),own.getId());assertTrue(passengers.getFavoriteRoutes(own.getId()).isEmpty());
    }
    @Test void uncheckedPreferencesAndNormalizedProfileArePersisted() {
        passengers.updateProfile(own.getId(),UserProfileDto.builder().firstName(" Changed ").lastName(" Passenger ").phone("+94 77 123 4567")
            .notifyByEmail(false).notifyBySms(false).delayAlertThresholdMinutes(60).build());
        var saved=users.findById(own.getId()).orElseThrow();assertEquals("0771234567",saved.getPhone());assertEquals("Changed",saved.getFirstName());
        assertFalse(saved.getNotifyByEmail());assertFalse(saved.getNotifyBySms());assertEquals(60,saved.getDelayAlertThresholdMinutes());
        assertTrue(encoder.matches("InitialPass123",saved.getPassword()));
    }
    @Test void resetIsHashedSingleUseAndNewCredentialsWork() {
        var principal=org.springframework.security.core.userdetails.User.withUsername(own.getEmail()).password("unused").roles("PASSENGER").build();
        sessionRegistry.registerNewSession("fixtureSession",principal);
        String raw=auth.createPasswordResetToken(own.getEmail());assertTrue(auth.validatePasswordResetToken(raw));
        assertNotEquals(raw,tokens.findAll().get(0).getToken());assertTrue(auth.resetPassword(raw,"ChangedPass123"));
        assertTrue(sessionRegistry.getSessionInformation("fixtureSession").isExpired());
        assertFalse(auth.validatePasswordResetToken(raw));assertFalse(auth.resetPassword(raw,"DifferentPass123"));
        var saved=users.findById(own.getId()).orElseThrow();assertTrue(encoder.matches("ChangedPass123",saved.getPassword()));assertFalse(encoder.matches("InitialPass123",saved.getPassword()));
    }
    @Test void issuingAgainInvalidatesOldLinkAndAnExpiredLinkCannotChangePassword() {
        String old=auth.createPasswordResetToken(own.getEmail());String fresh=auth.createPasswordResetToken(own.getEmail());
        assertFalse(auth.validatePasswordResetToken(old));assertFalse(auth.resetPassword(old,"ChangedPass123"));assertTrue(auth.validatePasswordResetToken(fresh));
        var stored=tokens.findByToken(AuthService.tokenHash(fresh)).orElseThrow();stored.setExpiryDate(java.time.LocalDateTime.now().minusSeconds(1));tokens.saveAndFlush(stored);
        assertFalse(auth.resetPassword(fresh,"ChangedPass123"));assertTrue(encoder.matches("InitialPass123",users.findById(own.getId()).orElseThrow().getPassword()));
    }
    @Test void simultaneousFavoriteCreationProducesOnlyOneRecord() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> operation=()->{start.await();try{passengers.addFavoriteRoute(own.getId(),pair("Kandy"));return true;}catch(IllegalArgumentException ex){return false;}};
        try {var a=executor.submit(operation);var b=executor.submit(operation);start.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,favorites.count());}
        finally {executor.shutdownNow();}
    }
    @Test void simultaneousRecoveryProducesOnlyOnePasswordChange() throws Exception {
        String raw=auth.createPasswordResetToken(own.getEmail());var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> operation=()->{start.await();return auth.resetPassword(raw,"ChangedPass123");};
        try {var a=executor.submit(operation);var b=executor.submit(operation);start.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(0,tokens.count());}
        finally {executor.shutdownNow();}
    }
}
