package com.cvforge.application.model;

import java.time.LocalDateTime;

/** Domain data for a saved job comparison and its optional tailored-resume draft. */
public final class JobComparisonData {
    private Long id;
    private String userId;
    private Long resumeId;
    private String profileLabel;
    private String jobText;
    private String resultJson;
    private String language;
    private Long tailoredResumeId;
    private String tailoredResumeJson;
    private String tailoredResumeTitle;
    private String tailoredResumeLayout;
    private LocalDateTime tailoredResumeGeneratedAt;
    private String tailoredResumeSourceHash;
    private LocalDateTime createdAt;

    public JobComparisonData() { }
    public JobComparisonData(String userId, Long resumeId, String profileLabel, String jobText, String resultJson, String language) {
        this.userId=userId; this.resumeId=resumeId; this.profileLabel=profileLabel; this.jobText=jobText;
        this.resultJson=resultJson; this.language=language; this.createdAt=LocalDateTime.now();
    }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public Long getResumeId(){return resumeId;} public void setResumeId(Long v){resumeId=v;}
    public String getProfileLabel(){return profileLabel;} public void setProfileLabel(String v){profileLabel=v;}
    public String getJobText(){return jobText;} public void setJobText(String v){jobText=v;}
    public String getResultJson(){return resultJson;} public void setResultJson(String v){resultJson=v;}
    public String getLanguage(){return language;} public void setLanguage(String v){language=v;}
    public Long getTailoredResumeId(){return tailoredResumeId;} public void setTailoredResumeId(Long v){tailoredResumeId=v;}
    public String getTailoredResumeJson(){return tailoredResumeJson;} public void setTailoredResumeJson(String v){tailoredResumeJson=v;}
    public String getTailoredResumeTitle(){return tailoredResumeTitle;} public void setTailoredResumeTitle(String v){tailoredResumeTitle=v;}
    public String getTailoredResumeLayout(){return tailoredResumeLayout;} public void setTailoredResumeLayout(String v){tailoredResumeLayout=v;}
    public LocalDateTime getTailoredResumeGeneratedAt(){return tailoredResumeGeneratedAt;} public void setTailoredResumeGeneratedAt(LocalDateTime v){tailoredResumeGeneratedAt=v;}
    public String getTailoredResumeSourceHash(){return tailoredResumeSourceHash;} public void setTailoredResumeSourceHash(String v){tailoredResumeSourceHash=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
