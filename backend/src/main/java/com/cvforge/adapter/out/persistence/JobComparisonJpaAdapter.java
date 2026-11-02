package com.cvforge.adapter.out.persistence;

import com.cvforge.application.model.JobComparisonData;
import com.cvforge.application.port.out.JobComparisonStore;
import com.cvforge.entity.JobComparison;
import com.cvforge.repository.JobComparisonRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** JPA adapter translating comparison entities to/from domain data. */
@Repository
public class JobComparisonJpaAdapter implements JobComparisonStore {
    private final JobComparisonRepository repository;
    public JobComparisonJpaAdapter(JobComparisonRepository repository){this.repository=repository;}
    @Override public JobComparisonData save(JobComparisonData data){return toDomain(repository.save(toEntity(data)));}
    @Override public Optional<JobComparisonData> findByIdAndUser(Long id,String userId){return repository.findByIdAndUserId(id,userId).map(this::toDomain);}
    @Override public List<JobComparisonData> findByUser(String userId){return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();}
    @Override public List<JobComparisonData> findRecentlyTailoredByUser(String userId,LocalDateTime after){return repository.findByUserIdAndTailoredResumeGeneratedAtAfterOrderByTailoredResumeGeneratedAtDesc(userId,after).stream().map(this::toDomain).toList();}
    @Override public void delete(JobComparisonData data){repository.findByIdAndUserId(data.getId(),data.getUserId()).ifPresent(repository::delete);}
    private JobComparisonData toDomain(JobComparison e){
        JobComparisonData d=new JobComparisonData(e.getUserId(),e.getResumeId(),e.getProfileLabel(),e.getJobText(),e.getResultJson(),e.getLanguage());
        d.setId(e.getId()); d.setTailoredResumeId(e.getTailoredResumeId()); d.setTailoredResumeJson(e.getTailoredResumeJson());
        d.setTailoredResumeTitle(e.getTailoredResumeTitle()); d.setTailoredResumeLayout(e.getTailoredResumeLayout());
        d.setTailoredResumeGeneratedAt(e.getTailoredResumeGeneratedAt()); d.setTailoredResumeSourceHash(e.getTailoredResumeSourceHash()); d.setCreatedAt(e.getCreatedAt());
        return d;
    }
    private JobComparison toEntity(JobComparisonData d){
        JobComparison e=new JobComparison(d.getUserId(),d.getResumeId(),d.getProfileLabel(),d.getJobText(),d.getResultJson(),d.getLanguage());
        e.setId(d.getId()); e.setTailoredResumeId(d.getTailoredResumeId()); e.setTailoredResumeJson(d.getTailoredResumeJson());
        e.setTailoredResumeTitle(d.getTailoredResumeTitle()); e.setTailoredResumeLayout(d.getTailoredResumeLayout());
        e.setTailoredResumeGeneratedAt(d.getTailoredResumeGeneratedAt()); e.setTailoredResumeSourceHash(d.getTailoredResumeSourceHash());
        if(d.getCreatedAt()!=null)e.setCreatedAt(d.getCreatedAt());
        return e;
    }
}
