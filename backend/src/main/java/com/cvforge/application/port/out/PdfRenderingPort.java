package com.cvforge.application.port.out;

public interface PdfRenderingPort {
    byte[] generatePdfFromJson(String jsonData, String layout);
}
