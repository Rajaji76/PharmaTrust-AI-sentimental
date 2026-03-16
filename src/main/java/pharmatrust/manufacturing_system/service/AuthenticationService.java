package pharmatrust.manufacturing_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pharmatrust.manufacturing_system.dto.AuthResponse;
import pharmatrust.manufacturing_system.dto.LoginRequest;
import pharmatrust.manufacturing_system.dto.RegisterRequest;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthenticationService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("User with email " + request.getEmail() + " already exists");
        }
        
        // Create new user
        User user = new User();
        // Do NOT set ID manually — @GeneratedValue(UUID) handles it
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setOrganization(request.getOrganization());
        user.setRole(request.getRole() != null ? request.getRole() : User.Role.MANUFACTURER);
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        // Shop identity fields
        user.setShopName(request.getShopName());
        user.setShopAddress(request.getShopAddress());
        user.setLicenseNumber(request.getLicenseNumber());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setGstNumber(request.getGstNumber());
        user.setCityState(request.getCityState());
        
        User savedUser = userRepository.save(user);
        
        // Generate tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        
        return new AuthResponse(
            token,
            refreshToken,
            savedUser.getEmail(),
            savedUser.getFullName(),
            savedUser.getRole(),
            jwtService.getExpirationTime()
        );
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );
        
        // Load user details
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Check if user is active
        if (!user.getIsActive()) {
            throw new RuntimeException("User account is deactivated");
        }
        
        // Update last login
        user.setLastLogin(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Generate tokens
        String token = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        
        return new AuthResponse(
            token,
            refreshToken,
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            jwtService.getExpirationTime()
        );
    }
    
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        if (jwtService.validateToken(refreshToken, userDetails)) {
            User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            String newToken = jwtService.generateToken(userDetails);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails);
            
            return new AuthResponse(
                newToken,
                newRefreshToken,
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTime()
            );
        }
        
        throw new RuntimeException("Invalid refresh token");
    }
    
    @Transactional
    public void logout(String email) {
        // In a production system, you would invalidate the token here
        // For now, we just update the user's last activity
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
