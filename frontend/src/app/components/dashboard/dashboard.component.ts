import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ComparisonResumePreview, JobComparison, JobMatchResult, ResumeData, ResumeLayout, ResumeRecord } from '../../models/resume.model';
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
  readonly maxJobWords = 3000;
  companyName = '';
  analyzingJob = false;
  jobAnalysis: JobMatchResult | null = null;
  jobError = '';
  comparisons: SavedComparisonCard[] = [];
  savingComparison = false;
  comparisonSaved = false;
  comparisonError = '';
  comparisonPdfId: number | null = null;
  comparisonPdfError = '';
  comparisonPreview: ComparisonResumePreview | null = null;
  comparisonPreviewTab: 'changes' | 'resume' = 'resume';
  savingComparisonPreview = false;

  get jobDescriptionWordCount(): number {
    const text = this.jobDescription.trim();
    return text ? text.split(/\s+/u).length : 0;
  }

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
    if (this.jobDescriptionWordCount > this.maxJobWords) { this.jobError = this.i18n.text('jobDescriptionLimitExceeded'); return; }
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
      jobText: this.jobDescription.trim(), result: { ...this.jobAnalysis, companyName: this.companyName.trim() || this.extractCompanyName(this.jobDescription) }, language: this.i18n.lang()
    };
    if (this.matchSource === 'saved' && this.matchResumeId) request.resumeId = this.matchResumeId;
    this.resumesApi.saveJobComparison(request).subscribe({
      next: saved => { const snapshot = this.comparisonSnapshot(saved); this.comparisons = [snapshot, ...this.comparisons.filter(item => item.id !== saved.id)]; this.savingComparison = false; this.comparisonSaved = true; },
      error: () => { this.savingComparison = false; this.comparisonError = this.i18n.text('comparisonSaveError'); }
    });
  }
  downloadComparisonPdf(item: SavedComparisonCard): void {
    this.comparisonPdfError = '';
    if (!item.resumeId && !item.tailoredResumeId) { this.comparisonPdfError = this.i18n.text('comparisonPdfUnavailable'); return; }
    if (this.comparisonPdfId !== null) return;
    this.comparisonPdfId = item.id;
    if (item.tailoredResumeId) {
      this.resumesApi.downloadComparisonResumePdf(item.id).subscribe({
        next: blob => { this.downloadComparisonBlob(blob, this.comparisonFilename(item)); this.comparisonPdfId = null; },
        error: failure => { this.handleComparisonPreviewError(failure); this.comparisonPdfId = null; }
      });
      return;
    }
    this.resumesApi.previewComparisonResume(item.id).subscribe({
      next: preview => { this.setComparisonPreview(preview, item.id); this.comparisonPdfId = null; },
      error: failure => { this.handleComparisonPreviewError(failure); this.comparisonPdfId = null; }
    });
  }
  saveComparisonPreview(): void {
    if (!this.comparisonPreview || this.savingComparisonPreview) return;
    const preview = this.comparisonPreview;
    const item = this.comparisons.find(card => card.id === this.comparisonPreviewComparisonId);
    if (!item) { this.comparisonPdfError = this.i18n.text('comparisonPdfError'); return; }
    this.savingComparisonPreview = true;
    this.resumesApi.saveComparisonResumePreview(item.id, preview.token).subscribe({
      next: blob => {
        this.downloadComparisonBlob(blob, preview.filename);
        this.savingComparisonPreview = false;
        this.comparisonPreview = null;
        this.loadComparisons();
        this.load();
      },
      error: failure => { this.handleComparisonPreviewError(failure); this.savingComparisonPreview = false; }
    });
  }
  closeComparisonPreview(): void { if (!this.savingComparisonPreview) this.comparisonPreview = null; }
  onComparisonPreviewTabKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    this.comparisonPreviewTab = event.key === 'ArrowLeft'
      ? (this.comparisonPreviewTab === 'resume' ? 'changes' : 'resume')
      : (this.comparisonPreviewTab === 'changes' ? 'resume' : 'changes');
    document.getElementById(this.comparisonPreviewTab === 'changes' ? 'comparison-changes-tab' : 'comparison-resume-tab')?.focus();
  }
  private comparisonPreviewComparisonId: number | null = null;
  previewContact(data: ResumeData): string { return [data.email, data.phone, data.location].filter(value => !!value).join(' · '); }
  previewChangeLabel(section: string): string {
    const labels: Record<string, string> = { headline: 'previewHeadline', summary: 'previewSummary', experience: 'previewExperience', projects: 'previewProjects', skills: 'previewSkills' };
    return this.i18n.text(labels[section] ?? 'comparisonChanges');
  }
  private setComparisonPreview(preview: ComparisonResumePreview, comparisonId: number): void {
    preview.data = this.resumesApi.parse({ jsonData: JSON.stringify(preview.data) } as ResumeRecord);
    this.comparisonPreview = preview;
    this.comparisonPreviewTab = 'resume';
    this.comparisonPreviewComparisonId = comparisonId;
  }
  private comparisonFilename(item: SavedComparisonCard): string {
    const slug = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/gi, '_').replace(/^_+|_+$/g, '').toLowerCase();
    const base = slug(item.profileLabel.replace(/\.(pdf|docx?|json)$/i, '')) || 'curriculo';
    const company = slug(item.result.companyName || '') || 'empresa';
    return base + '_' + company + '.pdf';
  }
  private downloadComparisonBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob), anchor = document.createElement('a');
    anchor.href = url; anchor.download = filename; anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  private handleComparisonPreviewError(failure: unknown): void {
    let message = this.i18n.text('comparisonPdfError');
    const body = (failure as { error?: unknown } | null)?.error;
    if (body instanceof Blob) {
      void body.text().then(text => {
        try {
          const parsed: unknown = JSON.parse(text);
          if (parsed && typeof parsed === 'object' && 'message' in parsed && typeof parsed.message === 'string' && parsed.message.trim()) this.comparisonPdfError = parsed.message;
        } catch { this.comparisonPdfError = message; }
      }).catch(() => { this.comparisonPdfError = message; });
    } else {
      this.comparisonPdfError = message;
    }
  }
  openComparison(item: SavedComparisonCard): void {
    this.jobAnalysis = this.copyResult(item.result); this.jobDescription = item.jobText; this.companyName = item.result.companyName ?? ''; this.comparisonSaved = true; this.comparisonError = '';
    if (item.resumeId && this.resumes.some(resume => resume.id === item.resumeId)) { this.matchSource = 'saved'; this.matchResumeId = item.resumeId; }
    const panel = document.querySelector('.job-match-panel');
    if (panel) window.scrollTo({ top: panel.getBoundingClientRect().top + window.scrollY - 24, behavior: 'smooth' });
  }
  private extractCompanyName(text: string): string {
    const patterns = [
      /(?:^|\n)\s*Logo\s+da\s+empresa\s+([^\r\n.]+)\.?/i,
      /(?:^|\n)\s*Estatísticas[^\r\n]*?\bsobre\s+a\s+empresa\s+([^\r\n]+)/i,
      /(?:^|\n)\s*A\s+empresa\s+(.+?)\s+(?:contrata|contratou|está\s+contratando)\b[^\r\n]*/i,
      /(?:^|\n)\s*([\p{L}][\p{L}\p{N}&.'’ -]{1,80}?),\s+a\s+[\p{L}][\p{L}\p{N}&.'’ -]*\s+company\b[^\r\n]*/iu,
      /(?:^|\n)\s*(?:nome\s+da\s+empresa|empresa|companhia|company|employer)\s*[:|–—-]\s*([^\r\n]+)/i,
      /(?:^|\n)\s*(?:sobre\s+(?:a\s+)?(?:empresa|companhia|company)|about\s+(?:the\s+)?company)\s*[:|–—-]\s*([^\r\n]+)/i,
      /(?:^|\n)\s*(?:empresa|company)\s*[:|]?\s*\r?\n\s*([^\r\n]+)/i
    ];
    for (const pattern of patterns) {
      const match = text.match(pattern);
      const candidate = match?.[1]?.split(/\s+[|–—]\s+|\s+-\s+(?=(?:vaga|oportunidade|job|position)\b)/i)[0]
        .replace(/\s+/g, ' ').trim().replace(/[.,;:|–—-]+$/, '').trim();
      if (candidate && candidate.length <= 180) return candidate;
    }
    return '';
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
