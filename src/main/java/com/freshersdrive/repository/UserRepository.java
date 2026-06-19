package com.freshersdrive.repository;

import com.freshersdrive.entity.User;
import com.freshersdrive.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    List<User> findAllByRole(Role role);

    // Returns only students who have email notifications turned ON
    List<User> findAllByRoleAndEmailAlertsTrue(Role role);
}