package com.parth.mealtrack.repo;

import com.parth.mealtrack.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {}
