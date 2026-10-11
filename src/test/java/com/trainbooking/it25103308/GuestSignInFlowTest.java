package com.trainbooking.it25103308;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25101520.controller.AuthController;
import com.trainbooking.it25101520.service.AuthService;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.controller.BookingController;
import com.trainbooking.it25103308.dto.SeatAvailability;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTML -> saved request -> real security login -> real continuation, using mock business services. */
class GuestSignInFlowTest {
    @Configuration @EnableWebMvc @Import({SecurityConfig.class, GlobalExceptionHandler.class})
    static class WebOnly {
        @Bean UserDetailsService users() {
            return new InMemoryUserDetailsManager(org.springframework.security.core.userdetails.User.withUsername("fixture@test.invalid")
                .password(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("FixturePass123"))
                .roles("PASSENGER").build());
        }
        @Bean BookingService bookings() { return mock(BookingService.class); }
        @Bean ScheduleRepository schedules() { return mock(ScheduleRepository.class); }
        @Bean PassengerService passengers() { return mock(PassengerService.class); }
        @Bean BookingController bookingController(BookingService b, ScheduleRepository s, PassengerService p) { return new BookingController(b,s,p); }
        @Bean AuthController authController() { return new AuthController(mock(AuthService.class)); }
        @Bean SpringTemplateEngine templates() {
            var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");
            var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;
        }
        @Bean ThymeleafViewResolver views(SpringTemplateEngine e) { var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v; }
    }
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private BookingService bookings;
    private final LocalDate date=LocalDate.now().plusDays(2);
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        bookings=context.getBean(BookingService.class);
        when(context.getBean(ScheduleRepository.class).findById(5L)).thenReturn(Optional.of(Schedule.builder().id(5L)
            .departureTime(LocalTime.of(8,30)).arrivalTime(LocalTime.of(11,5)).firstClassFare(BigDecimal.valueOf(1500))
            .secondClassFare(BigDecimal.valueOf(800)).build()));
        when(bookings.getSeatAvailability(eq(5L),eq(date),anyString())).thenReturn(new SeatAvailability(10,10,List.of(),null));
    }
    @AfterEach void close() { context.close(); }
    private String attribute(String html,String regex) {
        var matcher=Pattern.compile(regex).matcher(html);
        assertTrue(matcher.find(),"Missing generated HTML attribute matching "+regex);
        return org.springframework.web.util.HtmlUtils.htmlUnescape(matcher.group(1));
    }

    @ParameterizedTest @ValueSource(strings={"en-US","en-GB","fr-FR","si-LK"})
    void renderedGuestLinksRefreshAndFormUseTheSameIsoDate(String tag) throws Exception {
        String html=mvc.perform(get("/booking/seats?scheduleId=5&date="+date+"&seatClass=SECOND").locale(Locale.forLanguageTag(tag)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String target="/booking/sign-in?scheduleId=5&date="+date+"&seatClass=SECOND";
        assertEquals(target,attribute(html,"<a[^>]*href=\"([^\"]*booking/sign-in[^\"]*)\""));
        assertEquals(target,attribute(html,"data-sign-in-url=\"([^\"]+)\""));
        String refresh=attribute(html,"<a[^>]*href=\"([^\"]*booking/seats[^\"]*)\"");
        assertEquals("/booking/seats?scheduleId=5&date="+date,refresh);
        assertEquals(date.toString(),attribute(html,"name=\"travelDate\"[^>]*value=\"([^\"]+)\""));
        mvc.perform(get(refresh).locale(Locale.ROOT)).andExpect(status().isOk()).andExpect(model().attribute("date",date));
        verify(bookings,never()).createBooking(any(),any());
    }

    @ParameterizedTest @CsvSource({"en-US,link","en-GB,link","en-US,button","fr-FR,button"})
    void actualRenderedLinkCompletesLoginAndReopensFreshAvailability(String tag,String control) throws Exception {
        var locale=Locale.forLanguageTag(tag);
        var session=new MockHttpSession();
        String html=mvc.perform(get("/booking/seats?scheduleId=5&date="+date+"&seatClass=SECOND").session(session).locale(locale))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String link=attribute(html,control.equals("link") ? "<a[^>]*href=\"([^\"]*booking/sign-in[^\"]*)\"" : "data-sign-in-url=\"([^\"]+)\"");
        mvc.perform(get(link).session(session).locale(locale)).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/login").session(session).locale(locale)).andExpect(status().isOk());
        String resume=mvc.perform(post("/login").session(session).locale(locale).with(csrf())
            .param("username","fixture@test.invalid").param("password","FixturePass123"))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String seatUrl=mvc.perform(get(resume).session(session).locale(Locale.ROOT)).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/booking/seats?scheduleId=5&date="+date+"&seatClass=SECOND"))
            .andReturn().getResponse().getRedirectedUrl();
        String signedInHtml=mvc.perform(get(seatUrl).session(session).locale(locale)).andExpect(status().isOk())
            .andExpect(model().attribute("canReserve",true)).andReturn().getResponse().getContentAsString();
        assertFalse(signedInHtml.contains("data-sign-in-url="));
        assertTrue(signedInHtml.contains("Continue to Payment"));
        verify(bookings,times(2)).getSeatAvailability(5L,date,"FIRST");
        verify(bookings,times(2)).getSeatAvailability(5L,date,"SECOND");
        verify(bookings,never()).createBooking(any(),any());
        verify(bookings,never()).releaseLock(any());
        verifyNoInteractions(context.getBean(PassengerService.class));
    }

    @Test void obsoleteLocaleFormattedContinuationShowsUsefulRecoveryInsteadOfJson() throws Exception {
        mvc.perform(get("/booking/sign-in?scheduleId=5&date=10%2F5%2F26&seatClass=SECOND&continue")
            .with(user("fixture@test.invalid").roles("PASSENGER")))
            .andExpect(redirectedUrl("/trains/search"))
            .andExpect(flash().attribute("searchError","This journey link is invalid or outdated. Please search again to choose your travel date."));
        verifyNoInteractions(bookings);
    }
}
