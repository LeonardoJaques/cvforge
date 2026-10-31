package com.cvforge.repository;

import com.cvforge.entity.JobComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface JobComparisonRepository extends JpaRepository<JobComparison, Long> {
    List<JobComparison> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<JobComparison> findByIdAndUserId(Long id, String userId);
    List<JobComparison> findByUserIdAndTailoredResumeGeneratedAtAfterOrderByTailoredResumeGeneratedAtDesc(String userId, LocalDateTime after);
}
