import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { ResumeData, ResumeLayout, ResumeRecord } from '../../models/resume.model';
import { I18nService } from '../../services/i18n.service';
import { ResumeService } from '../../services/resume.service';

@Component({
  selector: 'app-preview', standalone: true, imports: [CommonModule, RouterLink],
  templateUrl: './preview.component.html', styleUrl: './preview.component.scss'
})
export class PreviewComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ResumeService);
  record: ResumeRecord | null = null;
  data: ResumeData | null = null;
  loading = true;
  error = false;
  downloadingPdf = false;
  pdfSizeMessage = '';
  isDraft = false;
  draftTitle = '';
  private draftLayout: ResumeLayout = 'ATS';
  private returnUrl = '/resume/new';
  private resumeEditorState: unknown;
  ngOnInit(): void {
    this.route.paramMap.pipe(switchMap(params => {
      const value = params.get('id');
      if (value === 'draft') {
        const state = window.history.state ?? {};
        const draft = state['resumeDraft'] as { title?: string; data?: ResumeData; layout?: ResumeLayout } | undefined;
        this.record = null; this.isDraft = true; this.draftTitle = draft?.title ?? ''; this.draftLayout = draft?.layout ?? 'ATS';
        this.data = draft?.data ?? null; this.returnUrl = typeof state['returnUrl'] === 'string' ? state['returnUrl'] : '/resume/new';
        this.resumeEditorState = state['resumeEditorState']; this.loading = false; this.error = !this.data;
        return of(null);
      }
      this.isDraft = false;
      const id = Number(value);
      this.loading = true; this.error = false;
      if (!Number.isInteger(id) || id <= 0) return of(null);
      return this.api.get(id).pipe(catchError(() => of(null)));
    })).subscribe(record => {
      if (this.isDraft) { this.loading = false; this.error = !this.data; return; }
      this.record = record;
      this.data = record ? this.api.parse(record) : null;
      this.loading = false;
      this.error = record === null;
    });
  }
  get layout(): ResumeLayout { return this.record?.layout ?? this.draftLayout; }
  backToEditor(): void { void this.router.navigateByUrl(this.returnUrl, { state: { resumeEditorState: this.resumeEditorState } }); }
  formatText(value: string): string {
    const escaped = String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    return escaped.split('\n').map(line => line
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/_(.+?)_/g, '<em>$1</em>')
      .replace(/^\s*•\s?(.*)$/, '<span class="formatted-bullet">• $1</span>')
      .replace(/^\s*(\d+)\.\s?(.*)$/, '<span class="formatted-number">$1. $2</span>'))
      .join('<br>');
  }
  print(): void { window.print(); }
  downloadPdf(): void {
    if (!this.record || this.downloadingPdf) return;
    this.downloadingPdf = true;
    this.pdfSizeMessage = '';
    this.api.downloadPdf(this.record.id).subscribe({
      next: blob => {
        const locale = this.i18n.lang() === 'pt' ? 'pt-BR' : 'en-US';
        const format = (bytes: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(bytes);
        const kb = blob.size / 1024;
        const mb = blob.size / (1024 * 1024);
        this.pdfSizeMessage = `${this.i18n.text('pdfSizeLabel')}: ${format(kb)} KB (${format(mb)} MB)`;
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${(this.data?.name || this.record?.title || 'curriculo').replace(/[^a-z0-9_-]+/gi, '_')}.pdf`;
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.downloadingPdf = false;
      },
      error: () => {
        this.downloadingPdf = false;
        this.pdfSizeMessage = this.i18n.text('previewError');
      }
    });
  }
  externalUrl(value: string): string | null {
    const trimmed = String(value ?? '').trim();
    if (!trimmed) return null;
    const candidate = /^https?:\/\//i.test(trimmed) ? trimmed : /^(?:www\.|[\w-]+\.)[\w.-]+(?:\/|$)/i.test(trimmed) ? `https://${trimmed}` : '';
    try {
      const parsed = new URL(candidate);
      return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : null;
    } catch { return null; }
  }
}
