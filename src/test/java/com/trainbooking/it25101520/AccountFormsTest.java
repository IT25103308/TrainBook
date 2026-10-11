package com.trainbooking.it25101520;

import com.trainbooking.it25101520.controller.*;
import com.trainbooking.it25101520.dto.*;
import com.trainbooking.it25101520.model.*;
import com.trainbooking.it25101520.service.*;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountFormsTest {
    @Configuration @EnableWebMvc @Import(com.trainbooking.config.SecurityConfig.class)
    static class WebOnly {
        @Bean org.springframework.security.core.userdetails.UserDetailsService users() { return new org.springframework.security.provisioning.InMemoryUserDetailsManager(); }
        @Bean PassengerService passengers() { return mock(PassengerService.class); }
        @Bean AuthService auth() { return mock(AuthService.class); }
        @Bean BookingRepository bookings() { return mock(BookingRepository.class); }
        @Bean PassengerController passengerController(PassengerService p,BookingRepository b) { return new PassengerController(p,b,mock(BookingHistoryService.class)); }
        @Bean AuthController authController(AuthService auth) { return new AuthController(auth); }
        @Bean com.trainbooking.config.AccessPageController accessPageController() { return new com.trainbooking.config.AccessPageController(); }
        @Bean SpringTemplateEngine templates() {
            var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");
            var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;
        }
        @Bean ThymeleafViewResolver views(SpringTemplateEngine e) { var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v; }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc; PassengerService passengers; AuthService auth;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .defaultRequest(get("/").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("fixture@test.invalid").roles("PASSENGER")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).build();passengers=context.getBean(PassengerService.class);auth=context.getBean(AuthService.class);
        when(passengers.getUserByEmail("fixture@test.invalid")).thenReturn(User.builder().id(7L).firstName("Test").lastName("Passenger").email("fixture@test.invalid").build());
        when(passengers.getUserProfile(7L)).thenReturn(UserProfileDto.builder().firstName("Test").lastName("Passenger").phone("0771234567")
            .notifyByEmail(false).notifyBySms(false).delayAlertThresholdMinutes(30).build());
        when(passengers.getStationNames()).thenReturn(List.of("Colombo Fort","Galle","Kandy"));
        when(passengers.getFavoriteRoutes(7L)).thenReturn(List.of(FavoriteRoute.builder().id(9L).origin("Colombo Fort").destination("Kandy").build()));
        when(auth.isConsoleResetDelivery()).thenReturn(true);
    }
    @AfterEach void cleanup() { context.close(); }
    @Test void profileRendersRealDataAndFavoriteCrudWithoutPhantomFields() throws Exception {
        String html=mvc.perform(get("/passenger/profile").principal(()->"fixture@test.invalid")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("name=\"nic\""));assertFalse(html.contains("Total Trips Taken"));assertFalse(html.contains("Standard Commute"));
        assertTrue(html.contains("/passenger/favorites/9/edit"));assertTrue(html.contains("name=\"_notifyBySms\""));
        assertTrue(html.contains("value=\"30\" selected=\"selected\""));
        Files.writeString(Path.of("target/profile-preview.html"),html);
    }
    @Test void uncheckedPreferencesBindFalseAndAccountEmailCannotBeChanged() throws Exception {
        mvc.perform(post("/passenger/profile").principal(()->"fixture@test.invalid").param("firstName","Test").param("lastName","Passenger")
            .param("phone","+94771234567").param("delayAlertThresholdMinutes","15").param("_notifyByEmail","on").param("_notifyBySms","on")
            .param("email","other@test.invalid").param("role","ADMIN"))
            .andExpect(redirectedUrl("/passenger/profile")).andExpect(flash().attributeExists("successMessage"));
        verify(passengers).updateProfile(eq(7L),argThat(f->Boolean.FALSE.equals(f.getNotifyByEmail()) && Boolean.FALSE.equals(f.getNotifyBySms()) && f.getEmail()==null));
    }
    @Test void invalidProfilePreservesInputAndDoesNotSave() throws Exception {
        mvc.perform(post("/passenger/profile").principal(()->"fixture@test.invalid").param("firstName","Still Here").param("lastName","Passenger")
            .param("phone","0771234567oops").param("delayAlertThresholdMinutes","wrong"))
            .andExpect(view().name("it25101520/profile")).andExpect(model().attributeHasFieldErrors("profileForm","phone","delayAlertThresholdMinutes"));
        verify(passengers,never()).updateProfile(any(),any());
    }
    @Test void favoriteErrorIsShownOnTheSameScreen() throws Exception {
        doThrow(new IllegalArgumentException("This route is already in your favourites.")).when(passengers).addFavoriteRoute(eq(7L),any());
        String html=mvc.perform(post("/passenger/favorites/add").principal(()->"fixture@test.invalid").param("origin","Colombo Fort").param("destination","Kandy"))
            .andExpect(view().name("it25101520/profile")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("already in your favourites"));
    }
    @Test void favoriteEditRendersAndSavesThroughOwnedId() throws Exception {
        when(passengers.getFavoriteRoute(9L,7L)).thenReturn(FavoriteRoute.builder().id(9L).origin("Colombo Fort").destination("Kandy").build());
        String html=mvc.perform(get("/passenger/favorites/9/edit").principal(()->"fixture@test.invalid")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target/favorite-edit-preview.html"),html);
        mvc.perform(post("/passenger/favorites/9/edit").principal(()->"fixture@test.invalid").param("origin","Colombo Fort").param("destination","Galle"))
            .andExpect(redirectedUrl("/passenger/profile#favourites"));
        verify(passengers).updateFavoriteRoute(eq(9L),eq(7L),argThat(f->"Galle".equals(f.getDestination())));
    }
    @Test void shortResetPasswordCannotReachService() throws Exception {
        mvc.perform(post("/reset-password").param("token","fixture").param("password","short").param("confirmPassword","short"))
            .andExpect(view().name("it25101520/reset-password"));verify(auth,never()).resetPassword(anyString(),anyString());
    }
    @Test void resetSuccessUsesExactNewPasswordAndReturnsToLogin() throws Exception {
        when(auth.resetPassword("fixture"," Exact Pass123 ")).thenReturn(true);
        mvc.perform(post("/reset-password").param("token","fixture").param("password"," Exact Pass123 ").param("confirmPassword"," Exact Pass123 "))
            .andExpect(redirectedUrl("/login?resetSuccess=true"));
    }
    @Test void recoveryPagesClearlyIdentifyConsoleModeAndNeverEchoPassword() throws Exception {
        String forgot=mvc.perform(get("/forgot-password")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(forgot.contains("Email delivery is disabled"));Files.writeString(Path.of("target/forgot-password-preview.html"),forgot);
        when(auth.validatePasswordResetToken("fixture")).thenReturn(true);
        String reset=mvc.perform(get("/reset-password").param("token","fixture")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(reset.contains("minlength=\"8\""));assertTrue(reset.contains("data-password-toggle=\"confirmPassword\""));
        Files.writeString(Path.of("target/reset-password-preview.html"),reset);
    }
    @Test void forgedConsentAndInvalidPhonesCannotRegister() throws Exception {
        mvc.perform(post("/register").param("firstName","Test").param("lastName","Passenger").param("email","fixture@test.invalid")
            .param("password","SecurePass123").param("confirmPassword","SecurePass123").param("phone","0771234567x"))
            .andExpect(model().attributeHasFieldErrors("registerRequest","phone","acceptedTerms"));
        verify(auth,never()).registerUser(any());
    }
    @Test void permissionFailureHasAUsablePageAndCorrectHttpStatus() throws Exception {
        String html=mvc.perform(get("/access-denied")).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Access Restricted"));assertTrue(html.contains("/trains/search"));
        Files.writeString(Path.of("target/access-denied-preview.html"),html);
    }
    @Test void recoveryResponseDoesNotRevealWhetherDeliveryOrAccountExistenceFailed() throws Exception {
        var a=mvc.perform(post("/forgot-password").param("email","fixture@test.invalid")).andExpect(status().isOk()).andReturn().getModelAndView();
        doThrow(new IllegalStateException("Delivery unavailable")).when(auth).sendPasswordResetEmail("fixture@test.invalid");
        var b=mvc.perform(post("/forgot-password").param("email","fixture@test.invalid")).andExpect(status().isOk()).andReturn().getModelAndView();
        assertEquals(a.getViewName(),b.getViewName());
        assertEquals(a.getModel().get("successMessage"),b.getModel().get("successMessage"));
        assertFalse(a.getModel().containsKey("errorMessage"));assertFalse(b.getModel().containsKey("errorMessage"));
    }
}
