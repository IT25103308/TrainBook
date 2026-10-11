package com.trainbooking.it25101520;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.config.FaviconController;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the actual security filter chain with non-mutating probes, never a live database. */
class AccountSecurityTest {
    @RestController static class Probes {
        @RequestMapping({"/","/trains/search","/booking/seats","/notifications/map","/trains/live-map",
                "/api/trains/locations","/api/trains/5/location","/api/trains/live-status","/passenger/profile",
                "/passenger/favorites/add","/passenger/bookings","/booking","/booking/sign-in","/payment/process","/rebooking/claim/example",
                "/validate","/api/tickets/validate","/dashboard/boarding-logs","/dashboard/validate/1/reset",
                "/trains/fleet","/trains/routes/add","/schedules","/notifications/status","/admin/trains/status",
                "/api/trains/location/update","/dashboard","/api/dashboard/reconciliation"})
        public String probe() { return "allowed"; }
    }
    @Configuration @EnableWebMvc @Import({SecurityConfig.class,FaviconController.class,GlobalExceptionHandler.class})
    static class WebOnly implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
            registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
        }
        @Bean Probes probes() { return new Probes(); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserDetailsService userDetails(UserRepository users) { return new UserDetailsServiceImpl(users); }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void cleanup() { context.close(); }
    @Test void guestsCanReadSearchSeatMapAndIntendedMapApis() throws Exception {
        for(String path:List.of("/","/trains/search","/booking/seats","/notifications/map","/trains/live-map",
                "/api/trains/locations","/api/trains/5/location","/api/trains/live-status")) mvc.perform(get(path)).andExpect(status().isOk());
    }
    @Test void guestsCannotAccessPersonalOrOperationalPagesEvenByDirectUrl() throws Exception {
        for(String path:List.of("/passenger/profile","/passenger/bookings","/rebooking/claim/example","/validate",
                "/dashboard/boarding-logs","/trains/fleet","/dashboard","/api/dashboard/reconciliation")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        }
    }
    @Test void roleMatrixSeparatesPassengersCoordinatorsStaffAndAdmins() throws Exception {
        Map<String,Set<String>> permissions=Map.of(
            "/trains/fleet",Set.of("ADMIN","COORDINATOR"),
            "/notifications/status",Set.of("ADMIN","COORDINATOR"),
            "/validate",Set.of("ADMIN","STATION_STAFF"),
            "/dashboard/boarding-logs",Set.of("ADMIN","STATION_STAFF"),
            "/dashboard",Set.of("ADMIN"),
            "/api/dashboard/reconciliation",Set.of("ADMIN"));
        for(String role:List.of("PASSENGER","COORDINATOR","STATION_STAFF","ADMIN")) {
            for(var entry:permissions.entrySet()) mvc.perform(get(entry.getKey()).with(user("fixture").roles(role)))
                    .andExpect(status().is(entry.getValue().contains(role)?200:403));
        }
    }
    @Test void mutationRolesAndCsrfAreBothEnforced() throws Exception {
        Map<String,Set<String>> permissions=Map.of(
            "/api/trains/location/update",Set.of("ADMIN","COORDINATOR"),
            "/api/tickets/validate",Set.of("ADMIN","STATION_STAFF"),
            "/dashboard/validate/1/reset",Set.of("ADMIN"),
            "/trains/routes/add",Set.of("ADMIN","COORDINATOR"));
        for(String role:List.of("PASSENGER","COORDINATOR","STATION_STAFF","ADMIN")) {
            for(var entry:permissions.entrySet()) {
                mvc.perform(post(entry.getKey()).with(user("fixture").roles(role))).andExpect(status().isForbidden());
                mvc.perform(post(entry.getKey()).with(user("fixture").roles(role)).with(csrf()))
                    .andExpect(status().is(entry.getValue().contains(role)?200:403));
            }
        }
        mvc.perform(post("/passenger/favorites/add").with(user("fixture").roles("PASSENGER"))).andExpect(status().isForbidden());
        mvc.perform(post("/passenger/favorites/add").with(user("fixture").roles("PASSENGER")).with(csrf())).andExpect(status().isOk());
    }
    @Test void authenticationNormalizesEmailButPreservesPassword() throws Exception {
        var users=context.getBean(UserRepository.class);
        var encoder=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(users.findByEmail("fixture@test.invalid")).thenReturn(Optional.of(com.trainbooking.it25101520.model.User.builder()
            .email("fixture@test.invalid").password(encoder.encode(" Exact Pass123 ")).role(com.trainbooking.it25101520.model.User.Role.PASSENGER).build()));
        mvc.perform(post("/login").with(csrf()).param("username"," FIXTURE@TEST.INVALID ").param("password"," Exact Pass123 "))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).param("username","fixture@test.invalid").param("password","Exact Pass123"))
            .andExpect(redirectedUrl("/login?error"));
    }
    @Test void logoutRequiresPostWithCsrf() throws Exception {
        mvc.perform(post("/logout").with(user("fixture"))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("fixture")).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }
    @Test void signInKeepsTheSelectedJourneyThroughAProtectedGet() throws Exception {
        var encoder=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(context.getBean(UserRepository.class).findByEmail("fixture@test.invalid")).thenReturn(Optional.of(com.trainbooking.it25101520.model.User.builder()
            .email("fixture@test.invalid").password(encoder.encode("FixturePass123")).role(com.trainbooking.it25101520.model.User.Role.PASSENGER).build()));
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/booking/sign-in?scheduleId=2&date=2026-10-06&seatClass=SECOND").session(session))
            .andExpect(status().is3xxRedirection());
        var response=mvc.perform(post("/login").session(session).with(csrf()).param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertTrue(response.getRedirectedUrl().contains("/booking/sign-in"));
        org.junit.jupiter.api.Assertions.assertTrue(response.getRedirectedUrl().contains("seatClass=SECOND"));
    }
    @Test void aBlockedReservationPostIsNeverReplayedAsAnUnsupportedGet() throws Exception {
        var encoder=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(context.getBean(UserRepository.class).findByEmail("fixture@test.invalid")).thenReturn(Optional.of(com.trainbooking.it25101520.model.User.builder()
            .email("fixture@test.invalid").password(encoder.encode("FixturePass123")).role(com.trainbooking.it25101520.model.User.Role.PASSENGER).build()));
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/booking").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").session(session).with(csrf()).param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(redirectedUrl("/"));
    }

    private void fixtureLogin() {
        var encoder=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(context.getBean(UserRepository.class).findByEmail("fixture@test.invalid")).thenReturn(Optional.of(com.trainbooking.it25101520.model.User.builder()
            .email("fixture@test.invalid").password(encoder.encode("FixturePass123")).role(com.trainbooking.it25101520.model.User.Role.PASSENGER).build()));
    }

    @Test void browserIconIsPublicAndServesAnActualImage() throws Exception {
        mvc.perform(get("/favicon.ico?continue"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/images/favicon.svg"));
        mvc.perform(get("/images/favicon.svg"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")));
    }

    @Test void iconRequestBeforeLoginDoesNotBecomeTheDestination() throws Exception {
        fixtureLogin();
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/favicon.ico").session(session)).andExpect(redirectedUrl("/images/favicon.svg"));
        mvc.perform(get("/images/favicon.svg").session(session)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        mvc.perform(post("/login").session(session).with(csrf()).param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(redirectedUrl("/"));
    }

    @Test void backgroundRequestsCannotOverwriteTheSavedPaymentJourney() throws Exception {
        fixtureLogin();
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/booking/sign-in?scheduleId=2&date=2026-10-06&seatClass=SECOND").session(session))
            .andExpect(status().is3xxRedirection());
        var expected=session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        mvc.perform(get("/favicon.ico").session(session)).andExpect(redirectedUrl("/images/favicon.svg"));
        mvc.perform(get("/other/favicon.ico").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/dashboard/reconciliation").accept("application/json").session(session))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/passenger/profile").header("X-Requested-With","XMLHttpRequest").session(session))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/passenger/bookings").accept("text/event-stream").session(session))
            .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertSame(expected, session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        mvc.perform(post("/login").session(session).with(csrf()).param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(redirectedUrl("http://localhost/booking/sign-in?scheduleId=2&date=2026-10-06&seatClass=SECOND&continue"));
    }

    @Test void missingStaticResourceIs404AndNeverSavesASignInTarget() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/css/missing.css").session(session)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("The requested page or resource was not found."));
        org.junit.jupiter.api.Assertions.assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test void aProtectedPaymentPageStillReturnsAfterSignIn() throws Exception {
        fixtureLogin();
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/booking/24/payment").session(session).accept("text/html"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/favicon.ico").session(session)).andExpect(redirectedUrl("/images/favicon.svg"));
        mvc.perform(post("/login").session(session).with(csrf()).param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(redirectedUrl("http://localhost/booking/24/payment?continue"));
    }
}
