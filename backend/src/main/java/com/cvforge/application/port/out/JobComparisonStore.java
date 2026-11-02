package com.cvforge.application.port.out;

import com.cvforge.application.model.JobComparisonData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence port for comparisons and tailored resume previews. */
public interface JobComparisonStore {
    JobComparisonData save(JobComparisonData comparison);
    Optional<JobComparisonData> findByIdAndUser(Long id, String userId);
    List<JobComparisonData> findByUser(String userId);
    List<JobComparisonData> findRecentlyTailoredByUser(String userId, LocalDateTime after);
    void delete(JobComparisonData comparison);
}
