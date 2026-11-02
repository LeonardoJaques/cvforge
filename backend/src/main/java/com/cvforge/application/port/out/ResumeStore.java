package com.cvforge.application.port.out;

import com.cvforge.application.model.ResumeData;
import java.util.List;
import java.util.Optional;

/** Outbound persistence port used by resume use cases. */
public interface ResumeStore {
    List<ResumeData> findAllByUser(String userId);
    Optional<ResumeData> findByIdAndUser(Long id, String userId);
    List<ResumeData> findByUserAndLayout(String userId, String layout);
    List<ResumeData> searchByTitle(String userId, String title);
    ResumeData save(ResumeData resume);
    void delete(ResumeData resume);
    long countByUser(String userId);
}
