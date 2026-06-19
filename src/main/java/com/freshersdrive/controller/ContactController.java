package com.freshersdrive.controller;

import com.freshersdrive.dto.ContactRequest;
import com.freshersdrive.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> submitContactForm(@RequestBody ContactRequest request) {
        if (request.name() == null || request.name().isBlank() ||
            request.email() == null || request.email().isBlank() ||
            request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body("Name, email, and message are required");
        }

        emailService.sendContactFormEmail(request.name(), request.email(), request.message());
        return ResponseEntity.ok().build();
    }
}