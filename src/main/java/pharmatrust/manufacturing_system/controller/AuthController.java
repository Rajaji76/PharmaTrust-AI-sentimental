package pharmatrust.manufacturing_system.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import pharmatrust.manufacturing_system.dto.AuthResponse;
import pharmatrust.manufacturing_system.dto.LoginRequest;
import pharmatrust.manufacturing_system.dto.RegisterRequest;
import pharmatrust.manufacturing_system.entity.User;
import pharmatrust.manufacturing_system.repository.UserRepository;
import pharmatrust.manufacturing_system.service.AuthenticationService;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    
    @Autowired
    private AuthenticationService authenticationService;
    
    @Autowired
    private UserRepository userRepository;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String refreshToken) {
        String token = refreshToken.substring(7); // Remove "Bearer " prefix
        AuthResponse response = authenticationService.refreshToken(token);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication != null) {
            authenticationService.logout(authentication.getName());
        }
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUser(Authentication authentication) {
        if (authentication != null) {
            return ResponseEntity.ok(authentication.getName());
        }
        return ResponseEntity.status(401).body("Unauthorized");
    }

    /**
     * GET /api/v1/auth/my-verification-status
     * Returns verification status for the currently logged-in distributor/retailer.
     */
    @GetMapping("/my-verification-status")
    public ResponseEntity<?> getMyVerificationStatus(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("isVerified", user.getIsVerified());
        resp.put("verifiedAt", user.getVerifiedAt() != null ? user.getVerifiedAt().toString() : null);
        resp.put("verifiedBy", user.getVerifiedBy() != null ? user.getVerifiedBy() : null);
        resp.put("role", user.getRole().name());
        resp.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        resp.put("shopName", user.getShopName() != null ? user.getShopName() : "");
        return ResponseEntity.ok(resp);
    }
}

