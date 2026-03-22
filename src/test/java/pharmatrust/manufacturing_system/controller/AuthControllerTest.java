package pharmatrust.manufacturing_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import pharmatrust.manufacturing_system.config.JwtAuthenticationFilter;
import pharmatrust.manufacturing_system.config.SecurityConfig;
import pharmatrust.manufacturing_system.dto.AuthResponse;
import pharmatrust.manufacturing_system.dto.LoginRequest;
import pharmatrust.manufacturing_system.dto.RegisterRequest;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UserRepository;
import pharmatrust.manufacturing_system.service.AuthenticationService;
import pharmatrust.manufacturing_system.service.JwtService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AuthController
 * Tests: registration, login, token refresh, unauthorized access
 * Requirements: FR-001, FR-002, NFR-006
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private AuthResponse buildAuthResponse() {
        return new AuthResponse(
                "test-jwt-token",
                "test-refresh-token",
                "user@pharmatrust.com",
                "Test User",
                User.Role.MANUFACTURER,
                86400000L);
    }

    // ==================== Register ====================

    @Test
    void register_validRequest_returns200() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@pharmatrust.com");
        request.setPassword("SecurePass1!");
        request.setFullName("New User");
        request.setOrganization("PharmaCorp");

        when(authenticationService.register(any(RegisterRequest.class)))
                .thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.email").value("user@pharmatrust.com"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("SecurePass1!");
        request.setFullName("Test");
        request.setOrganization("Org");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("short");
        request.setFullName("Test");
        request.setOrganization("Org");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returns500() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@pharmatrust.com");
        request.setPassword("SecurePass1!");
        request.setFullName("Test");
        request.setOrganization("Org");

        when(authenticationService.register(any()))
                .thenThrow(new RuntimeException("User with email existing@pharmatrust.com already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // ==================== Login ====================

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest("user@pharmatrust.com", "password123");

        when(authenticationService.login(any(LoginRequest.class)))
                .thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.role").value("MANUFACTURER"));
    }

    @Test
    void login_invalidCredentials_returns500() throws Exception {
        LoginRequest request = new LoginRequest("user@pharmatrust.com", "wrongpassword");

        when(authenticationService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== Get Current User ====================

    @Test
    void getCurrentUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Verification Status ====================

    @Test
    void getMyVerificationStatus_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/my-verification-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyVerificationStatus_withValidToken_returns200() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@pharmatrust.com");
        user.setRole(User.Role.MANUFACTURER);
        user.setIsVerified(false);

        org.springframework.security.core.userdetails.UserDetails userDetails =
                org.springframework.security.core.userdetails.User.builder()
                        .username("user@pharmatrust.com")
                        .password("encoded")
                        .authorities("MANUFACTURER")
                        .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@pharmatrust.com");
        when(jwtService.validateToken("valid-token", userDetails)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("user@pharmatrust.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("user@pharmatrust.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/auth/my-verification-status")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.role").value("MANUFACTURER"));
    }
}
