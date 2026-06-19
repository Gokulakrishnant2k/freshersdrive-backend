package com.freshersdrive.repository;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriveChatRepository extends JpaRepository<DriveChatMessage, Long> {

    // ✅ CLEAN + SAFE + JPA STANDARD
    List<DriveChatMessage> findByDriveOrderByTimestampAsc(Drive drive);
}