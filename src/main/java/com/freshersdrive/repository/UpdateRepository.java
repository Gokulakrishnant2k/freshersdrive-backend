package com.freshersdrive.repository;

import com.freshersdrive.entity.Update;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UpdateRepository extends JpaRepository<Update, Long> {

    List<Update> findAllByOrderByCreatedAtDesc();
}