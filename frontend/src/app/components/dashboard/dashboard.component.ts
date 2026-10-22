import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { JobComparison, JobMatchResult, ResumeLayout, ResumeRecord } from '../../models/resume.model';
import { I18nService } from '../../services/i18n.service';
import { ResumeService } from '../../services/resume.service';

interface SavedComparisonCard extends JobComparison {
  savedScore: number;
}

@Component({
  selector: 'app-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './dashboard.component.html', styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly resumesApi = inject(ResumeService);
  private readonly router = inject(Router);
  resumes: ResumeRecord[] = [];
  loading = true;
  error = false;
  query = '';
  layoutFilter = 'ALL';
  deletingId: number | null = null;
  deleteError = false;
  importing = false;
  importError = '';
  importingProfileText = false;
  profileText = '';
  profileTextImportError = '';
  matchSource: 'saved' | 'text' = 'text';
  matchResumeId: number | null = null;
  matchProfileText = '';
  jobDescription = '';
  analyzingJob = false;
  jobAnalysis: JobMatchResult | null = null;
  jobError = '';
  comparisons: SavedComparisonCard[] = [];
  savingComparison = false;
  comparisonSaved = false;
  comparisonError = '';

  ngOnInit(): void { this.load(); this.loadComparisons(); }
  loadComparisons(): void {
    this.resumesApi.listJobComparisons().subscribe({ next: data => this.comparisons = (data ?? []).map(item => this.comparisonSnapshot(item)), error: () => this.comparisonError = this.i18n.text('comparisonLoadError') });
  }
  load(): void {
    this.loading = true; this.error = false;
    this.resumesApi.list().subscribe({
      next: data => { this.resumes = data ?? []; if (this.matchResumeId === null && this.resumes.length) this.matchResumeId = this.resumes[0].id; this.loading = false; },
      error: () => { this.error = true; this.loading = false; }
    });
  }
  get filtered(): ResumeRecord[] {
    const needle = this.query.trim().toLocaleLowerCase();
    return this.resumes.filter(item => (this.layoutFilter === 'ALL' || item.layout === this.layoutFilter)
      && (!needle || `${item.title} ${this.resumesApi.parse(item).name} ${this.resumesApi.parse(item).headline}`.toLocaleLowerCase().includes(needle)));
  }
  data(item: ResumeRecord) { return this.resumesApi.parse(item); }
  initials(item: ResumeRecord): string { return this.resumesApi.initials(this.data(item).name || item.title); }
  layoutName(layout: string): string {
    if (layout === 'EXECUTIVO') return this.i18n.text('layoutExecutive');
    if (layout === 'TERMINAL') return this.i18n.text('layoutTerminal');
    if (layout === 'MODERNO') return this.i18n.text('layoutModernTitle');
    if (layout === 'CRIATIVO') return this.i18n.text('layoutCreativeTitle');
    if (layout === 'COMPACTO') return this.i18n.text('layoutCompactTitle');
    return this.i18n.text('layoutATS');
  }
  async remove(item: ResumeRecord): Promise<void> {
    if (!window.confirm(this.i18n.text('deleteConfirm'))) return;
    this.deleteError = false;
    this.deletingId = item.id;
    this.resumesApi.delete(item.id).subscribe({
      next: () => { this.resumes = this.resumes.filter(resume => resume.id !== item.id); this.deletingId = null; },
      error: () => { this.deleteError = true; this.deletingId = null; }
    });
  }
  importFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.importError = '';
    this.importing = true;
    this.resumesApi.importFile(file).subscribe({
      next: result => {
        this.importing = false;
        void this.router.navigate(['/resume/new'], { state: { importedResume: result } });
      },
      error: error => {
        this.importing = false;
        this.importError = error?.error?.message || this.i18n.text('importError');
      }
    });
  }
  importProfileText(): void {
    this.profileTextImportError = '';
    if (!this.profileText.trim()) { this.profileTextImportError = this.i18n.text('profileTextRequired'); return; }
    this.importingProfileText = true;
    this.resumesApi.importProfileText(this.profileText.trim()).subscribe({
      next: result => { this.importingProfileText = false; void this.router.navigate(['/resume/new'], { state: { importedResume: result } }); },
      error: error => { this.importingProfileText = false; this.profileTextImportError = error?.error?.message || this.i18n.text('profileImportError'); }
    });
  }
  analyzeJob(): void {
    this.jobError = ''; this.jobAnalysis = null; this.comparisonSaved = false; this.comparisonError = '';
    const request: { resumeId?: number; profileText?: string; jobText?: string; language?: 'pt' | 'en' } = {};
    if (this.matchSource === 'saved') {
      if (!this.matchResumeId) { this.jobError = this.i18n.text('selectResumeForMatch'); return; }
      request.resumeId = this.matchResumeId;
    } else {
      if (!this.matchProfileText.trim()) { this.jobError = this.i18n.text('profileTextRequired'); return; }
      request.profileText = this.matchProfileText.trim();
    }
    if (!this.jobDescription.trim()) { this.jobError = this.i18n.text('jobRequired'); return; }
    request.jobText = this.jobDescription.trim();
    request.language = this.i18n.lang();
    this.analyzingJob = true;
    this.resumesApi.analyzeJob(request).subscribe({
      next: result => { this.jobAnalysis = result; this.analyzingJob = false; },
      error: error => { this.jobError = error?.error?.message || this.i18n.text('jobAnalysisError'); this.analyzingJob = false; }
    });
  }
  saveComparison(): void {
    if (!this.jobAnalysis || !this.jobDescription.trim()) return;
    this.savingComparison = true; this.comparisonError = ''; this.comparisonSaved = false;
    const request: { resumeId?: number; profileLabel: string; jobText: string; result: JobMatchResult; language: 'pt' | 'en' } = {
      profileLabel: this.matchSource === 'saved' ? (this.resumes.find(item => item.id === this.matchResumeId)?.title || this.i18n.text('savedResume')) : this.i18n.text('pastedProfileText'),
      jobText: this.jobDescription.trim(), result: this.jobAnalysis, language: this.i18n.lang()
    };
    if (this.matchSource === 'saved' && this.matchResumeId) request.resumeId = this.matchResumeId;
    this.resumesApi.saveJobComparison(request).subscribe({
      next: saved => { const snapshot = this.comparisonSnapshot(saved); this.comparisons = [snapshot, ...this.comparisons.filter(item => item.id !== saved.id)]; this.savingComparison = false; this.comparisonSaved = true; },
      error: () => { this.savingComparison = false; this.comparisonError = this.i18n.text('comparisonSaveError'); }
    });
  }
  openComparison(item: SavedComparisonCard): void {
    this.jobAnalysis = this.copyResult(item.result); this.jobDescription = item.jobText; this.comparisonSaved = true; this.comparisonError = '';
    if (item.resumeId && this.resumes.some(resume => resume.id === item.resumeId)) { this.matchSource = 'saved'; this.matchResumeId = item.resumeId; }
    const panel = document.querySelector('.job-match-panel');
    if (panel) window.scrollTo({ top: panel.getBoundingClientRect().top + window.scrollY - 24, behavior: 'smooth' });
  }
  formatDate(value: string | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat(this.i18n.lang() === 'pt' ? 'pt-BR' : 'en-US', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
  }
  private comparisonSnapshot(item: JobComparison): SavedComparisonCard {
    const result = this.copyResult(item.result);
    return { ...item, result, savedScore: result.matchScore };
  }
  private copyResult(result: JobMatchResult): JobMatchResult {
    return {
      ...result,
      matchingRequirements: (result.matchingRequirements ?? []).map(item => ({ ...item })),
      missingRequirements: (result.missingRequirements ?? []).map(item => ({ ...item })),
      keywords: [...(result.keywords ?? [])],
      resumeSuggestions: [...(result.resumeSuggestions ?? [])],
      interviewQuestions: [...(result.interviewQuestions ?? [])]
    };
  }
}