package com.trainbooking.it25101520;

import com.trainbooking.it25101520.controller.AuthController;
import com.trainbooking.it25101520.dto.RegisterRequest;
import com.trainbooking.it25101520.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form binding, bean validation and Thymeleaf rendering; the account service is a mock. */
class RegistrationPasswordTest {
    @Configuration @EnableWebMvc
    static class WebOnly {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean AuthController authController(AuthService auth) { return new AuthController(auth); }
        @Bean SpringTemplateEngine templates() {
            var resolver=new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
            var engine=new SpringTemplateEngine(); engine.setTemplateResolver(resolver); return engine;
        }
        @Bean ThymeleafViewResolver views(SpringTemplateEngine engine) {
            var views=new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8"); return views;
        }
    }
    AnnotationConfigWebApplicationContext context; MockMvc mvc; AuthService auth;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(WebOnly.class); context.refresh(); auth=context.getBean(AuthService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void cleanup() { context.close(); }
    @ParameterizedTest @ValueSource(strings={"", "Mismatch123", "securePass123"})
    void missingOrDifferentConfirmationCannotRegister(String confirmation) throws Exception {
        String html=mvc.perform(post("/register").param("firstName","Test").param("lastName","Passenger")
                .param("email","fixture@test.invalid").param("phone","0771234567").param("acceptedTerms","true").param("password","SecurePass123").param("confirmPassword",confirmation))
            .andExpect(status().isOk()).andExpect(view().name("it25101520/register"))
            .andExpect(model().attributeHasFieldErrors("registerRequest","confirmPassword"))
            .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("value=\"SecurePass123\""));
        assertFalse(html.contains("value=\"Mismatch123\""));
        verifyNoInteractions(auth);
    }
    @Test void matchingPasswordsKeepTheirExactValueAndContinueToLogin() throws Exception {
        String password=" Secure Pass123 ";
        mvc.perform(post("/register").param("firstName","Test").param("lastName","Passenger")
                .param("email","fixture@test.invalid").param("phone","0771234567").param("acceptedTerms","true").param("password",password).param("confirmPassword",password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered=true"));
        verify(auth).registerUser(argThat(r->password.equals(r.getPassword()) && password.equals(r.getConfirmPassword())));
    }
    @Test void omittedConfirmationIsRejectedByServerValidation() throws Exception {
        mvc.perform(post("/register").param("firstName","Test").param("lastName","Passenger")
                .param("email","fixture@test.invalid").param("phone","0771234567").param("acceptedTerms","true").param("password","SecurePass123"))
            .andExpect(model().attributeHasFieldErrors("registerRequest","confirmPassword"));
        verifyNoInteractions(auth);
    }
    @Test void registrationAndLoginRenderControlsAndPolicyLinks() throws Exception {
        String register=mvc.perform(get("/register")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(register.contains("name=\"confirmPassword\"")); assertTrue(register.contains("autocomplete=\"new-password\""));
        assertTrue(register.contains("/terms\" target=\"_blank\"")); assertTrue(register.contains("/privacy\" target=\"_blank\""));
        assertTrue(register.contains("data-password-toggle=\"confirmPassword\""));
        String login=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(login.contains("autocomplete=\"current-password\"")); assertTrue(login.contains("data-password-toggle=\"password\""));
        Files.writeString(Path.of("target/registration-preview.html"),register);
        Files.writeString(Path.of("target/login-preview.html"),login);
    }
    @Test void directServiceInvocationCannotSaveMismatchedPasswords() {
        var users=mock(com.trainbooking.it25101520.repository.UserRepository.class);
        var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var tokens=mock(com.trainbooking.it25101520.repository.PasswordResetTokenRepository.class);
        var service=new AuthService(users,tokens,encoder,mock(com.trainbooking.it25101520.service.PasswordResetDelivery.class),mock(com.trainbooking.it25101520.service.AccountSessions.class));
        assertThrows(IllegalArgumentException.class,()->service.registerUser(RegisterRequest.builder()
                .firstName("Test").lastName("Passenger").email("fixture@test.invalid").phone("0771234567").acceptedTerms(true)
                .password("SecurePass123").confirmPassword("different").build()));
        verifyNoInteractions(users,encoder,tokens);
    }
}
