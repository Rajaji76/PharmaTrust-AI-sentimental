package pharmatrust.manufacturing_system.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import pharmatrust.manufacturing_system.dto.AuthResponse;
import pharmatrust.manufacturing_system.dto.LoginRequest;
import pharmatrust.manufacturing_system.dto.RegisterRequest;
import pharmatrust.manufacturing_system.service.AuthenticationService;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:5173", "http://10.184.81.201:3000", "http://10.184.81.201:5173"})
public class AuthController {
    
    @Autowired
    private AuthenticationService authenticationService;
    
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
}
