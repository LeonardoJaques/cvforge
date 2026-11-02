package com.cvforge.service;

import com.cvforge.application.model.ResumeData;
import com.cvforge.application.service.ResumeApplicationService;
import com.cvforge.application.port.out.ResumeStore;
import com.cvforge.application.port.in.ResumeUseCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceOwnershipTest {
    @Mock private ResumeStore resumeStore;
    @InjectMocks private ResumeApplicationService resumeService;

    @Test void cannotReadResumeOwnedByAnotherUser() {
        when(resumeStore.findByIdAndUser(42L, "alice")).thenReturn(Optional.empty());
        assertTrue(resumeService.getResumeById(42L, "alice").isEmpty());
        verify(resumeStore).findByIdAndUser(42L, "alice");
    }
    @Test void cannotUpdateResumeOwnedByAnotherUser() {
        when(resumeStore.findByIdAndUser(42L, "alice")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> resumeService.updateResume(42L, new ResumeUseCases.UpdateResumeCommand(null, null, null), "alice"));
        verify(resumeStore, never()).save(any());
    }
    @Test void cannotDeleteResumeOwnedByAnotherUser() {
        when(resumeStore.findByIdAndUser(42L, "alice")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> resumeService.deleteResume(42L, "alice"));
        verify(resumeStore, never()).delete(any());
    }
    @Test void createsAndThenDeletesAResumeOwnedByTheSameUser() {
        ResumeUseCases.CreateResumeCommand request = new ResumeUseCases.CreateResumeCommand(
                "leo", "CVForge import test", "{\"name\":\"Leonardo Jaques\"}", "ats", "CVForge test");
        when(resumeStore.save(any())).thenAnswer(invocation -> {
            ResumeData data = invocation.getArgument(0);
            return data.withIdAndTimestamps(91L, LocalDateTime.now(), LocalDateTime.now(), 0);
        });
        var created = resumeService.createResume(request);
        ResumeData persisted = new ResumeData(91L, "leo", "CVForge import test", "{\"name\":\"Leonardo Jaques\"}", "ATS", "CVForge test", LocalDateTime.now(), LocalDateTime.now(), 0);
        when(resumeStore.findByIdAndUser(91L, "leo")).thenReturn(Optional.of(persisted));
        resumeService.deleteResume(91L, "leo");
        assertEquals(91L, created.id());
        verify(resumeStore).save(argThat(data -> data.jsonData().equals("{\"name\":\"Leonardo Jaques\"}") && data.layout().equals("ATS")));
        verify(resumeStore).delete(persisted);
    }
    @Test void rejectsInvalidLayoutBeforeCallingPersistence() {
        ResumeUseCases.CreateResumeCommand request = new ResumeUseCases.CreateResumeCommand("leo", "CV", "{}", "UNKNOWN", null);
        assertThrows(IllegalArgumentException.class, () -> resumeService.createResume(request));
        verifyNoInteractions(resumeStore);
    }
}
