package com.cvforge.application.port.in;

import java.util.Optional;

/** Inbound port for generating resume PDFs. */
public interface ResumePdfUseCases {
    Optional<PdfDownload> forSavedResume(Long id, String userId);
    byte[] fromJson(String jsonData, String layout);

    record PdfDownload(byte[] bytes, String filename) { }
}
