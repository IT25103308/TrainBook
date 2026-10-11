package com.trainbooking.it25101520;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.it25101520.controller.AuthController;
import com.trainbooking.it25101520.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Public policy access uses the real security chain, without starting the application or database. */
class PolicyPagesTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class WebOnly {
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean AuthController authController(AuthService service) { return new AuthController(service); }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebOnly.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void cleanup() { context.close(); }

    @ParameterizedTest @ValueSource(strings = {"terms", "privacy"})
    void policiesAreReadableBeforeCreatingOrSigningIntoAnAccount(String page) throws Exception {
        mvc.perform(get("/" + page)).andExpect(status().isOk()).andExpect(view().name("it25101520/" + page));
        verifyNoInteractions(context.getBean(AuthService.class));
    }

    @ParameterizedTest @ValueSource(strings = {"terms", "privacy"})
    void policyTemplatesRenderWorkingNavigationWithoutJavascript(String page) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        MockServletContext servlet = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servlet); request.setRequestURI("/" + page);
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, new MockHttpServletResponse());
        String html = engine.process("it25101520/" + page, new WebContext(exchange, Locale.ENGLISH));
        assertTrue(html.contains("<main"));
        assertTrue(html.contains("href=\"/register\""));
        assertTrue(html.contains("href=\"/trains/search\""));
        assertTrue(html.contains("href=\"/" + (page.equals("terms") ? "privacy" : "terms") + "\""));
        assertFalse(html.contains("href=\"#\""));
        assertFalse(html.contains("<script"));
        assertFalse(java.util.regex.Pattern.compile("\\s+th:[a-z-]+\\s*=").matcher(html).find());
    }
}
