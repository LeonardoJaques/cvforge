package com.cvforge.application.port.in;

import com.cvforge.application.model.ImportedResume;

public interface ProfileTextImportUseCases {
    ImportedResume importText(String sourceText);
}
