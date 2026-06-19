package com.freshersdrive.controller;

import com.freshersdrive.dto.ChatRequest;
import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveChatMessage;
import com.freshersdrive.entity.User;
import com.freshersdrive.repository.DriveChatRepository;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class DriveChatController {

    private final DriveChatRepository repo;
    private final DriveRepository driveRepo;
    private final UserRepository userRepo;

    // SEND MESSAGE
    @PostMapping("/{driveId}")
    public DriveChatMessage sendMessage(
            @PathVariable Long driveId,
            @RequestBody ChatRequest req
    ) {

        Drive drive = driveRepo.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found"));

        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        DriveChatMessage msg = DriveChatMessage.builder()
                .drive(drive)
                .user(user)
                .message(req.getMessage())
                .build();

        return repo.save(msg);
    }

    // GET MESSAGES
    @GetMapping("/{driveId}")
    public List<DriveChatMessage> getMessages(@PathVariable Long driveId) {

        Drive drive = driveRepo.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found"));

        return repo.findByDriveOrderByTimestampAsc(drive);
    }
}