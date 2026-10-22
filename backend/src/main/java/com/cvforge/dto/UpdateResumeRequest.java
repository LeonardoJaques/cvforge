package com.cvforge.dto;

import jakarta.validation.constraints.Size;

public class UpdateResumeRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 350_000, message = "Dados do currículo excedem o limite")
    private String jsonData;

    @Size(max = 20)
    private String layout;

    public UpdateResumeRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getJsonData() { return jsonData; }
    public void setJsonData(String jsonData) { this.jsonData = jsonData; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
}
