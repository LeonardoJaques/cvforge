package com.cvforge.service;

import com.cvforge.dto.UpdateResumeRequest;
import com.cvforge.dto.CreateResumeRequest;
import com.cvforge.entity.Resume;
import com.cvforge.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class ResumeServiceOwnershipTest {

    @Mock
    private ResumeRepository resumeRepository;

    @InjectMocks
    private ResumeService resumeService;

    @Test
    void cannotReadResumeOwnedByAnotherUser() {
        when(resumeRepository.findByIdAndUserId(42L, "alice")).thenReturn(Optional.empty());

        assertTrue(resumeService.getResumeById(42L, "alice").isEmpty());
        verify(resumeRepository).findByIdAndUserId(42L, "alice");
    }

    @Test
    void cannotUpdateResumeOwnedByAnotherUser() {
        when(resumeRepository.findByIdAndUserId(42L, "alice")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> resumeService.updateResume(42L, new UpdateResumeRequest(), "alice"));
        verify(resumeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cannotDeleteResumeOwnedByAnotherUser() {
        when(resumeRepository.findByIdAndUserId(42L, "alice")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> resumeService.deleteResume(42L, "alice"));
        verify(resumeRepository, never()).delete((com.cvforge.entity.Resume) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsAndThenDeletesAResumeOwnedByTheSameUser() {
        CreateResumeRequest request = new CreateResumeRequest();
        request.setUserId("leo");
        request.setTitle("CVForge import test");
        request.setJsonData("{\"name\":\"Leonardo Jaques\"}");
        request.setLayout("ats");
        request.setCreatedBy("CVForge test");
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(91L);
            return resume;
        });

        var created = resumeService.createResume(request);
        Resume persisted = new Resume("leo", "CVForge import test", "{\"name\":\"Leonardo Jaques\"}", "ATS", "CVForge test");
        persisted.setId(91L);
        when(resumeRepository.findByIdAndUserId(91L, "leo")).thenReturn(Optional.of(persisted));

        resumeService.deleteResume(91L, "leo");

        assertEquals(91L, created.getId());
        verify(resumeRepository).save(argThat(resume -> resume.getJsonData().equals("{\"name\":\"Leonardo Jaques\"}") && resume.getLayout().equals("ATS")));
        verify(resumeRepository).delete(persisted);
    }
}
