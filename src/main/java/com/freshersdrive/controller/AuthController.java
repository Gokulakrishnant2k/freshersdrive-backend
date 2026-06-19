package com.freshersdrive.controller;

import com.freshersdrive.dto.AuthRequest;
import com.freshersdrive.dto.AuthResponse;
import com.freshersdrive.entity.User;
import com.freshersdrive.repository.UserRepository;
import com.freshersdrive.security.JwtUtils;
import com.freshersdrive.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils        jwtUtils;
    private final AuthService     authService;

    // ── POST /auth/register ──────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        String result = authService.register(user);

        return switch (result) {
            case "EMAIL_EXISTS"   -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is already registered."));
            case "INVALID_EMAIL"  -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid email format."));
            case "EMAIL_NOT_FOUND" -> ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "We couldn't verify that this email address exists. Please check it and try again."));
            case "WEAK_PASSWORD"  -> ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Password must be at least 8 characters and include a number and special character."));
            default               -> ResponseEntity.ok()
                    .body(Map.of("message",
                            "Registration successful! Please check your email to verify your account."));
        };
    }

    // ── GET /auth/verify-email?token=xxx ────────────────────────────────────

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        String result = authService.verifyEmail(token);

        return switch (result) {
            case "INVALID"          -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid or expired verification link."));
            case "ALREADY_VERIFIED" -> ResponseEntity.ok()
                    .body(Map.of("message", "Email already verified. Please log in."));
            default                 -> ResponseEntity.ok()
                    .body(Map.of("message", "Email verified successfully! You can now log in."));
        };
    }

    // ── POST /auth/login ─────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest.Login request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User not found."));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid password."));
        }

        // Block login if email not verified
        if (!user.isEmailVerified()) {                  // ← fixed field name
            return ResponseEntity.status(403)
                    .body(Map.of("message",
                            "Please verify your email before logging in. Check your inbox."));
        }

        String accessToken  = jwtUtils.generateTokenFromEmail(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(response);
    }

    // ── POST /auth/forgot-password ───────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody Map<String, String> body) {

        String email = body.getOrDefault("email", "").trim();

        if (email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is required."));
        }

        String result = authService.inititateForgotPassword(email);

        return switch (result) {
            case "INVALID_FORMAT" -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid email format."));
            case "NOT_GOOGLE"     -> ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Only Google accounts (@gmail.com or Google Workspace) are accepted."));
            default               -> ResponseEntity.ok()
                    .body(Map.of("message",
                            "If this email is registered, a reset link has been sent. Check your inbox."));
        };
    }

    // ── GET /auth/reset-password/validate?token=xxx ──────────────────────────

    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        boolean valid = authService.validateResetToken(token);

        if (!valid) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "message", "Link is invalid or expired."));
        }

        return ResponseEntity.ok(Map.of("valid", true));
    }

    // ── POST /auth/reset-password ────────────────────────────────────────────

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestBody Map<String, String> body) {

        String token    = body.getOrDefault("token", "");
        String password = body.getOrDefault("password", "");

        String result = authService.resetPassword(token, password);

        return switch (result) {
            case "INVALID"       -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Link is invalid or expired. Request a new one."));
            case "WEAK_PASSWORD" -> ResponseEntity.badRequest()
                    .body(Map.of("message", "Password must be at least 8 characters."));
            default              -> ResponseEntity.ok()
                    .body(Map.of("message", "Password reset successfully. You can now log in."));
        };
    }
}