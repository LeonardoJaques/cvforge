package com.cvforge.repository;

import com.cvforge.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long>, JpaSpecificationExecutor<Resume> {

    List<Resume> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Resume> findByIdAndUserId(Long id, String userId);

    List<Resume> findByTitleContainingIgnoreCaseAndUserId(String title, String userId);

    @Query("SELECT r FROM Resume r WHERE r.userId = :userId AND r.layout = :layout ORDER BY r.createdAt DESC")
    List<Resume> findByUserIdAndLayoutOrderByCreatedAtDesc(@Param("userId") String userId, @Param("layout") String layout);

    @Query("SELECT r FROM Resume r WHERE r.userId = :userId")
    List<Resume> findAllByUserId(@Param("userId") String userId);

    long countByUserId(String userId);
}
