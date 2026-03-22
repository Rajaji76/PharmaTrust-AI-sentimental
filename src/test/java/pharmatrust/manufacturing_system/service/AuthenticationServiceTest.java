package pharmatrust.manufacturing_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import pharmatrust.manufacturing_system.dto.AuthResponse;
import pharmatrust.manufacturing_system.dto.LoginRequest;
import pharmatrust.manufacturing_system.dto.RegisterRequest;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService
 * Tests: registration, login, token refresh, logout
 * Requirements: FR-001, FR-002, NFR-006
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@pharmatrust.com");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setFullName("Test User");
        testUser.setOrganization("PharmaCorp");
        testUser.setRole(User.Role.MANUFACTURER);
        testUser.setIsActive(true);

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("newuser@pharmatrust.com");
        registerRequest.setPassword("SecurePass123!");
        registerRequest.setFullName("New User");
        registerRequest.setOrganization("NewPharma");
        registerRequest.setRole(User.Role.MANUFACTURER);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@pharmatrust.com");
        loginRequest.setPassword("password123");
    }

    // ==================== Registration ====================

    @Test
    void register_newUser_returnsAuthResponse() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        AuthResponse response = authenticationService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_existingEmail_throwsException() {
        when(userRepository.findByEmail(registerRequest.getEmail()))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authenticationService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordIsEncoded() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        authenticationService.register(registerRequest);

        verify(passwordEncoder).encode("SecurePass123!");
    }

    @Test
    void register_defaultRoleIsManufacturer_whenRoleNotProvided() {
        registerRequest.setRole(null);

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertThat(saved.getRole()).isEqualTo(User.Role.MANUFACTURER);
            return testUser;
        });
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        authenticationService.register(registerRequest);
    }

    // ==================== Login ====================

    @Test
    void login_validCredentials_returnsAuthResponse() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userDetailsService.loadUserByUsername(loginRequest.getEmail())).thenReturn(userDetails);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(userRepository.save(any())).thenReturn(testUser);

        AuthResponse response = authenticationService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    void login_invalidCredentials_throwsException() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_deactivatedUser_throwsException() {
        testUser.setIsActive(false);

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authenticationService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void login_updatesLastLoginTimestamp() {
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any())).thenReturn("token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(userRepository.save(any())).thenReturn(testUser);

        authenticationService.login(loginRequest);

        verify(userRepository).save(argThat(u -> u.getLastLogin() != null));
    }

    // ==================== Token Refresh ====================

    @Test
    void refreshToken_validToken_returnsNewTokens() {
        String refreshToken = "valid-refresh-token";

        when(jwtService.extractUsername(refreshToken)).thenReturn(testUser.getEmail());
        when(userDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(userDetails);
        when(jwtService.validateToken(refreshToken, userDetails)).thenReturn(true);
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(userDetails)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("new-refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        AuthResponse response = authenticationService.refreshToken(refreshToken);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void refreshToken_invalidToken_throwsException() {
        String invalidToken = "invalid-token";

        when(jwtService.extractUsername(invalidToken)).thenReturn(testUser.getEmail());
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(jwtService.validateToken(invalidToken, userDetails)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.refreshToken(invalidToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // ==================== Logout ====================

    @Test
    void logout_existingUser_updatesTimestamp() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        authenticationService.logout(testUser.getEmail());

        verify(userRepository).save(argThat(u -> u.getUpdatedAt() != null));
    }

    @Test
    void logout_nonExistentUser_throwsException() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.logout("unknown@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }
}
