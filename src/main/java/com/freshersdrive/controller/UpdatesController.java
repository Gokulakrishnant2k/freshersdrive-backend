package com.freshersdrive.controller;

import com.freshersdrive.entity.Update;
import com.freshersdrive.repository.UpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/updates") // ✅ FIXED: removed /api prefix (context-path already adds it)
@RequiredArgsConstructor
public class UpdatesController {

    private final UpdateRepository updateRepository;

    @GetMapping
    public ResponseEntity<List<Update>> getAllUpdates() {
        return ResponseEntity.ok(
                updateRepository.findAllByOrderByCreatedAtDesc()
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Update> createUpdate(@RequestBody Update update) {
        update.setId(null);
        update.setCreatedAt(LocalDateTime.now());
        Update saved = updateRepository.save(update);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUpdate(@PathVariable Long id) {
        if (!updateRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        updateRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}