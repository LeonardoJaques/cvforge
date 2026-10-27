package com.cvforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateResumeRequest {

    @NotBlank(message = "userId é obrigatório")
    @Size(max = 50)
    private String userId;

    @NotBlank(message = "título é obrigatório")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "jsonData é obrigatório")
    @Size(max = 350_000, message = "Dados do currículo excedem o limite")
    private String jsonData;

    @NotBlank(message = "layout é obrigatório")
    @Size(max = 20)
    private String layout;

    @Size(max = 100)
    private String createdBy;

    public CreateResumeRequest() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getJsonData() { return jsonData; }
    public void setJsonData(String jsonData) { this.jsonData = jsonData; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
