package com.freshersdrive.security;

import com.freshersdrive.entity.User;
import com.freshersdrive.enums.Role;
import com.freshersdrive.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");

        log.info("OAuth2 login: email={}, name={}", email, name);

        // Only allow Google accounts (all Google emails are valid)
        if (email == null || !email.contains("@")) {
            response.sendRedirect(redirectUri + "?error=invalid_email");
            return;
        }

        // Create user if first time logging in via Google
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPassword(""); // no password for OAuth users
            newUser.setRole(Role.ROLE_USER);
            newUser.setEmailVerified(true); // ← fixed: was setIsVerified(true)
            return userRepository.save(newUser);
        });

        String accessToken  = jwtUtils.generateTokenFromEmail(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        String targetUrl = redirectUri
                + "?token="        + URLEncoder.encode(accessToken,  StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&name="         + URLEncoder.encode(user.getName() != null ? user.getName() : "", StandardCharsets.UTF_8)
                + "&email="        + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                + "&role="         + URLEncoder.encode(user.getRole().name(), StandardCharsets.UTF_8);

        log.info("Redirecting to: {}", targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}