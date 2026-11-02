package com.cvforge.adapter.out.persistence;

import com.cvforge.application.model.ResumeData;
import com.cvforge.application.port.out.ResumeStore;
import com.cvforge.entity.Resume;
import com.cvforge.repository.ResumeRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** JPA adapter translating between persistence entities and the domain model. */
@Repository
public class ResumeJpaAdapter implements ResumeStore {
    private final ResumeRepository repository;
    public ResumeJpaAdapter(ResumeRepository repository) { this.repository = repository; }

    @Override public List<ResumeData> findAllByUser(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }
    @Override public Optional<ResumeData> findByIdAndUser(Long id, String userId) {
        return repository.findByIdAndUserId(id, userId).map(this::toDomain);
    }
    @Override public List<ResumeData> findByUserAndLayout(String userId, String layout) {
        return repository.findByUserIdAndLayoutOrderByCreatedAtDesc(userId, layout).stream().map(this::toDomain).toList();
    }
    @Override public List<ResumeData> searchByTitle(String userId, String title) {
        return repository.findByTitleContainingIgnoreCaseAndUserId(title, userId).stream().map(this::toDomain).toList();
    }
    @Override public ResumeData save(ResumeData data) {
        Resume saved = repository.save(toEntity(data));
        return toDomain(saved);
    }
    @Override public void delete(ResumeData data) { repository.findByIdAndUserId(data.id(), data.userId()).ifPresent(repository::delete); }
    @Override public long countByUser(String userId) { return repository.countByUserId(userId); }

    private ResumeData toDomain(Resume entity) {
        return new ResumeData(entity.getId(), entity.getUserId(), entity.getTitle(), entity.getJsonData(), entity.getLayout(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
    private Resume toEntity(ResumeData data) {
        Resume entity = new Resume(data.userId(), data.title(), data.jsonData(), data.layout(), data.createdBy());
        if (data.id() != null) entity.setId(data.id());
        if (data.createdAt() != null) entity.setCreatedAt(data.createdAt());
        if (data.updatedAt() != null) entity.setUpdatedAt(data.updatedAt());
        if (data.version() != null) entity.setVersion(data.version());
        return entity;
    }
}
