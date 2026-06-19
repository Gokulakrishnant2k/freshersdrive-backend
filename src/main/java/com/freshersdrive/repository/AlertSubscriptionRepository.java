package com.freshersdrive.repository;

import com.freshersdrive.entity.AlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {
    Optional<AlertSubscription> findByUserId(Long userId);

    @Query("SELECT a FROM AlertSubscription a WHERE a.isActive = true AND " +
           "(a.preferredCategory IS NULL OR a.preferredCategory = :category) AND " +
           "(a.preferredBatch IS NULL OR a.preferredBatch = :batch)")
    List<AlertSubscription> findActiveSubscribersForDrive(
        @Param("category") String category,
        @Param("batch") Integer batch
    );
}