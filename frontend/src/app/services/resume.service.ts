import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { JobComparison, JobMatchResult, ResumeData, ResumeImportResult, ResumeLayout, ResumeRecord } from '../models/resume.model';

const API = `${new URL('api/v1/resumes', document.baseURI).pathname.replace(/\/$/, '')}`;

@Injectable({ providedIn: 'root' })
export class ResumeService {
  private readonly http = inject(HttpClient);
  list(): Observable<ResumeRecord[]> { return this.http.get<ResumeRecord[]>(API); }
  get(id: number): Observable<ResumeRecord> { return this.http.get<ResumeRecord>(`${API}/${id}`); }
  create(title: string, data: ResumeData, layout: ResumeLayout): Observable<ResumeRecord> {
    return this.http.post<ResumeRecord>(API, { userId: 'anonimo', title, jsonData: JSON.stringify(data), layout, createdBy: 'CVForge' });
  }
  update(id: number, title: string, data: ResumeData, layout: ResumeLayout): Observable<ResumeRecord> {
    return this.http.put<ResumeRecord>(`${API}/${id}`, { title, jsonData: JSON.stringify(data), layout });
  }
  importFile(file: File): Observable<ResumeImportResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ResumeImportResult>(`${API}/import`, body);
  }
  importProfileText(text: string): Observable<ResumeImportResult> { return this.http.post<ResumeImportResult>(`${API}/import/text`, { text }); }
  analyzeJob(request: { resumeId?: number; profileText?: string; jobText?: string; language?: 'pt' | 'en' }): Observable<JobMatchResult> {
    return this.http.post<JobMatchResult>(`${new URL('api/v1/jobs/analyze', document.baseURI).pathname.replace(/\/$/, '')}`, request);
  }
  listJobComparisons(): Observable<JobComparison[]> {
    return this.http.get<JobComparison[]>(`${new URL('api/v1/jobs/comparisons', document.baseURI).pathname.replace(/\/$/, '')}`);
  }
  saveJobComparison(request: { resumeId?: number; profileLabel: string; jobText: string; result: JobMatchResult; language: 'pt' | 'en' }): Observable<JobComparison> {
    return this.http.post<JobComparison>(`${new URL('api/v1/jobs/comparisons', document.baseURI).pathname.replace(/\/$/, '')}`, request);
  }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${API}/${id}`); }
  downloadPdf(id: number): Observable<Blob> { return this.http.get(`${API}/${id}/pdf`, { responseType: 'blob' }); }
  parse(record: ResumeRecord): ResumeData {
    try {
      const value: unknown = JSON.parse(record.jsonData);
      if (!value || typeof value !== 'object' || Array.isArray(value)) return this.emptyData();
      const data = value as Partial<ResumeData>;
      const experience = Array.isArray(data.experience) ? data.experience.filter(this.isObject).map(item => ({ role: this.string(item['role']), company: this.string(item['company']), period: this.string(item['period']), description: this.string(item['description']) })) : [];
      const education = Array.isArray(data.education) ? data.education.filter(this.isObject).map(item => ({ course: this.string(item['course']), institution: this.string(item['institution']), period: this.string(item['period']) })) : [];
      const courses = Array.isArray(data.courses) ? data.courses.filter(this.isObject).map(item => ({ course: this.string(item['course']), institution: this.string(item['institution']), period: this.string(item['period']) })) : [];
      const rawProjects = (value as Record<string, unknown>)['projects'];
      const projects = Array.isArray(rawProjects) ? rawProjects.filter(this.isObject).map(item => ({ name: this.string(item['name'] ?? item['title']), description: this.string(item['description']), link: this.string(item['link'] ?? item['url']) })) : [];
      return {
        name: this.string(data.name), headline: this.string(data.headline), email: this.string(data.email),
        phone: this.string(data.phone), location: this.string(data.location), summary: this.string(data.summary),
        experience, education, courses, projects,
        skills: Array.isArray(data.skills) ? data.skills.map(String) : [], links: Array.isArray(data.links) ? data.links.map(String) : [],
        ...(typeof data.photo === 'string' && data.photo.startsWith('data:image/') ? { photo: data.photo } : {})
      };
    } catch { return this.emptyData(); }
  }
  initials(name: string): string { return name.trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0).toUpperCase()).join('') || 'CV'; }
  private string(value: unknown): string { return typeof value === 'string' ? value : ''; }
  private isObject(value: unknown): value is Record<string, unknown> { return !!value && typeof value === 'object' && !Array.isArray(value); }
  private emptyData(): ResumeData { return { name: '', headline: '', email: '', phone: '', location: '', summary: '', experience: [], education: [], courses: [], projects: [], skills: [], links: [] }; }
}
