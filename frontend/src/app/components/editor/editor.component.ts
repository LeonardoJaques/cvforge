import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY_RESUME, EducationItem, ExperienceItem, ProjectItem, ResumeData, ResumeImportResult, ResumeLayout, ResumeRecord } from '../../models/resume.model';
import { I18nService } from '../../services/i18n.service';
import { ResumeService } from '../../services/resume.service';

@Component({
  selector: 'app-editor', standalone: true, imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './editor.component.html', styleUrl: './editor.component.scss'
})
export class EditorComponent implements OnInit {
  readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ResumeService);
  readonly layouts: { id: ResumeLayout; titleKey: string; detailKey: string; mark: string }[] = [
    { id: 'ATS', titleKey: 'layoutATS', detailKey: 'layoutAtsDetail', mark: '01' },
    { id: 'EXECUTIVO', titleKey: 'layoutExecutiveTitle', detailKey: 'layoutExecutiveDetail', mark: '02' },
    { id: 'TERMINAL', titleKey: 'layoutTerminalTitle', detailKey: 'layoutTerminalDetail', mark: '03' },
    { id: 'MODERNO', titleKey: 'layoutModernTitle', detailKey: 'layoutModernDetail', mark: '04' },
    { id: 'CRIATIVO', titleKey: 'layoutCreativeTitle', detailKey: 'layoutCreativeDetail', mark: '05' },
    { id: 'COMPACTO', titleKey: 'layoutCompactTitle', detailKey: 'layoutCompactDetail', mark: '06' }
  ];
  form = new FormGroup({
    title: new FormControl('', [Validators.maxLength(200)]),
    name: new FormControl('', [Validators.required, Validators.maxLength(160)]),
    headline: new FormControl('', [Validators.maxLength(160)]),
    email: new FormControl('', [Validators.email, Validators.maxLength(160)]),
    phone: new FormControl('', [Validators.maxLength(60)]),
    location: new FormControl('', [Validators.maxLength(120)]),
    photo: new FormControl(''),
    summary: new FormControl('', [Validators.maxLength(1500)]),
    skills: new FormControl(''),
    links: new FormControl(''),
    experience: new FormArray<FormGroup>([]),
    education: new FormArray<FormGroup>([]),
    courses: new FormArray<FormGroup>([]),
    projects: new FormArray<FormGroup>([])
  });
  selectedLayout: ResumeLayout = 'ATS';
  id: number | null = null;
  loading = false;
  saving = false;
  generatingPresentation = false;
  presentationMessage = '';
  skillTags: string[] = [];
  skillDraft = '';
  submitted = false;
  error = false;
  saveErrorMessage = '';
  notFound = false;
  photoError = '';
  importedWarnings: string[] = [];
  jsonMode = false;
  jsonText = '';
  jsonError = '';
  jsonNotice = '';
  private lastGeneratedPresentation = '';

  get experiences(): FormArray<FormGroup> { return this.form.controls.experience; }
  get educations(): FormArray<FormGroup> { return this.form.controls.education; }
  get courses(): FormArray<FormGroup> { return this.form.controls.courses; }
  get projects(): FormArray<FormGroup> { return this.form.controls.projects; }

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = Number(params.get('id'));
      if (Number.isInteger(id) && id > 0) { this.id = id; this.load(id); }
      else {
        this.id = null; this.loading = false; this.form.controls.title.reset(''); this.selectedLayout = 'ATS'; this.notFound = false; this.error = false; this.saveErrorMessage = '';
        const draft = window.history.state?.['resumeEditorState'] as { title?: string; data?: ResumeData; layout?: ResumeLayout; jsonMode?: boolean; jsonText?: string } | undefined;
        if (draft?.data) { this.form.controls.title.setValue(draft.title ?? ''); this.selectedLayout = draft.layout ?? 'ATS'; this.applyData(draft.data); this.jsonMode = draft.jsonMode ?? false; this.jsonText = draft.jsonText ?? ''; }
        const imported = window.history.state?.['importedResume'] as ResumeImportResult | undefined;
        this.importedWarnings = imported?.warnings ?? [];
        this.form.controls.title.setValue(imported?.title ?? '');
        this.applyData(imported?.data ?? EMPTY_RESUME);
        if (imported) window.history.replaceState({ ...window.history.state, importedResume: null }, '');
      }
    });
  }
  selectLayout(layout: ResumeLayout): void { this.selectedLayout = layout; }
  toggleJsonMode(): void { this.jsonMode = !this.jsonMode; this.jsonError = ''; this.jsonNotice = ''; if (this.jsonMode) this.syncJsonFromForm(); }
  syncJsonFromForm(): void { this.jsonText = JSON.stringify(this.buildData(), null, 2); this.jsonError = ''; this.jsonNotice = ''; }
  applyJson(): void {
    this.jsonError = ''; this.jsonNotice = '';
    try {
      const parsed: unknown = JSON.parse(this.jsonText);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(this.i18n.text('jsonObjectError'));
      const envelope = parsed as Record<string, unknown>;
      const source = envelope['data'] && typeof envelope['data'] === 'object' && !Array.isArray(envelope['data']) ? envelope['data'] : parsed;
      this.applyData(this.api.parse({ jsonData: JSON.stringify(source) } as ResumeRecord));
      if (typeof envelope['title'] === 'string') this.form.controls.title.setValue(envelope['title']);
      if (typeof envelope['layout'] === 'string' && this.layouts.some(item => item.id === envelope['layout'])) this.selectedLayout = envelope['layout'] as ResumeLayout;
      this.syncJsonFromForm(); this.jsonNotice = this.i18n.text('jsonApplied');
    } catch (error) { this.jsonError = error instanceof SyntaxError ? this.i18n.text('jsonSyntaxError') : (error instanceof Error ? error.message : this.i18n.text('jsonInvalid')); }
  }
  importJsonFile(event: Event): void {
    const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = '';
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) { this.jsonError = this.i18n.text('jsonTooLarge'); return; }
    const reader = new FileReader(); reader.onload = () => { this.jsonText = String(reader.result ?? ''); this.applyJson(); }; reader.onerror = () => { this.jsonError = this.i18n.text('jsonInvalid'); }; reader.readAsText(file);
  }
  exportJson(): void {
    const data = this.buildData(); const title = this.form.controls.title.value?.trim() || data.name || '';
    const blob = new Blob([JSON.stringify({ title, layout: this.selectedLayout, data }, null, 2)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${(title || 'curriculo').replace(/[^a-z0-9_-]+/gi, '_')}.json`; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  previewJson(): void {
    this.jsonError = ''; this.jsonNotice = '';
    try {
      const parsed: unknown = JSON.parse(this.jsonText);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(this.i18n.text('jsonObjectError'));
      const envelope = parsed as Record<string, unknown>;
      const source = envelope['data'] && typeof envelope['data'] === 'object' && !Array.isArray(envelope['data']) ? envelope['data'] : parsed;
      const data = this.api.parse({ jsonData: JSON.stringify(source) } as ResumeRecord);
      const title = typeof envelope['title'] === 'string' ? envelope['title'] : (this.form.controls.title.value || data.name);
      const layout = typeof envelope['layout'] === 'string' && this.layouts.some(item => item.id === envelope['layout']) ? envelope['layout'] as ResumeLayout : this.selectedLayout;
      const returnUrl = this.id === null ? '/resume/new' : `/resume/${this.id}`;
      void this.router.navigate(['/preview/draft'], { state: { resumeDraft: { title, data, layout }, returnUrl, resumeEditorState: { title: this.form.controls.title.value ?? '', data: this.buildData(), layout: this.selectedLayout, jsonMode: this.jsonMode, jsonText: this.jsonText } } });
    } catch (error) { this.jsonError = error instanceof SyntaxError ? this.i18n.text('jsonSyntaxError') : (error instanceof Error ? error.message : this.i18n.text('jsonInvalid')); }
  }
  moveLayout(event: KeyboardEvent, current: ResumeLayout): void {
    const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'];
    if (!keys.includes(event.key)) return;
    event.preventDefault();
    const direction = ['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : -1;
    const index = this.layouts.findIndex(layout => layout.id === current);
    const next = this.layouts[(index + direction + this.layouts.length) % this.layouts.length];
    this.selectedLayout = next.id;
    document.getElementById(`layout-${next.id}`)?.focus();
  }
  addExperience(value?: ExperienceItem): void {
    const period = this.parsePeriod(value?.period ?? '');
    const group = new FormGroup({ role: new FormControl(value?.role ?? '', Validators.maxLength(140)), company: new FormControl(value?.company ?? '', Validators.maxLength(140)), period: new FormControl(value?.period ?? ''), periodStart: new FormControl(period.start), periodEnd: new FormControl(period.end), periodCurrent: new FormControl(period.current), description: new FormControl(value?.description ?? '', Validators.maxLength(1200)) });
    if (period.current) group.get('periodEnd')?.disable({ emitEvent: false });
    if (period.start || period.end || period.current) this.updatePeriod(group);
    this.experiences.push(group);
  }
  addEducation(value?: EducationItem): void {
    const period = this.parsePeriod(value?.period ?? '');
    const group = new FormGroup({ course: new FormControl(value?.course ?? '', Validators.maxLength(160)), institution: new FormControl(value?.institution ?? '', Validators.maxLength(160)), period: new FormControl(value?.period ?? ''), periodStart: new FormControl(period.start), periodEnd: new FormControl(period.end), periodCurrent: new FormControl(period.current) });
    if (period.current) group.get('periodEnd')?.disable({ emitEvent: false });
    if (period.start || period.end || period.current) this.updatePeriod(group);
    this.educations.push(group);
  }
  addCourse(value?: EducationItem): void {
    this.courses.push(new FormGroup({ course: new FormControl(value?.course ?? '', Validators.maxLength(160)), institution: new FormControl(value?.institution ?? '', Validators.maxLength(160)), period: new FormControl(value?.period ?? '', Validators.maxLength(80)) }));
  }
  addProject(value?: ProjectItem): void {
    this.projects.push(new FormGroup({ name: new FormControl(value?.name ?? '', Validators.maxLength(160)), description: new FormControl(value?.description ?? '', Validators.maxLength(1200)), link: new FormControl(value?.link ?? '', Validators.maxLength(500)) }));
  }
  updatePeriod(group: FormGroup): void {
    const start = String(group.get('periodStart')?.value ?? '');
    const end = String(group.get('periodEnd')?.value ?? '');
    const current = group.get('periodCurrent')?.value === true;
    const startLabel = this.formatMonth(start);
    const endLabel = current ? this.i18n.text('present') : this.formatMonth(end);
    const parts = [startLabel, endLabel].filter(Boolean);
    group.get('period')?.setValue(parts.join(' — '));
  }
  toggleCurrentPeriod(group: FormGroup): void {
    const end = group.get('periodEnd');
    if (group.get('periodCurrent')?.value === true) end?.disable({ emitEvent: false });
    else end?.enable({ emitEvent: false });
    this.updatePeriod(group);
  }
  isControlInvalid(control: AbstractControl | null): boolean {
    return !!control && control.invalid && (control.touched || this.submitted);
  }
  fieldError(control: AbstractControl | null, label: string): string {
    if (!this.isControlInvalid(control) || !control) return '';
    const replace = (template: string, token: string, value: string) => template.replace(`{${token}}`, value);
    if (control.hasError('required')) return replace(this.i18n.text('fieldRequired'), 'field', label);
    if (control.hasError('email')) return replace(this.i18n.text('invalidEmail'), 'field', label);
    if (control.hasError('maxlength')) {
      const limit = String(control.getError('maxlength')?.requiredLength ?? '');
      return replace(replace(this.i18n.text('maxLength'), 'field', label), 'max', limit);
    }
    return replace(this.i18n.text('invalidField'), 'field', label);
  }
  get invalidFields(): string[] {
    const fields: string[] = [];
    const labels: Record<string, string> = {
      title: this.i18n.lang() === 'pt' ? 'Nome deste currículo' : 'Resume title',
      name: this.i18n.text('fullName'), headline: this.i18n.text('headline'), email: this.i18n.text('email'),
      phone: this.i18n.text('phone'), location: this.i18n.text('location'), summary: this.i18n.text('summary')
    };
    for (const [key, label] of Object.entries(labels)) {
      const control = this.form.get(key);
      if (control?.invalid) fields.push(this.fieldError(control, label));
    }
    this.experiences.controls.forEach((group, index) => {
      for (const [key, label] of [['role', this.i18n.text('role')], ['company', this.i18n.text('company')], ['description', this.i18n.text('description')]]) {
        const control = group.get(key);
        if (control?.invalid) fields.push(`${this.i18n.text('experience')} ${String(index + 1).padStart(2, '0')} · ${this.fieldError(control, label)}`);
      }
    });
    this.educations.controls.forEach((group, index) => {
      for (const [key, label] of [['course', this.i18n.text('course')], ['institution', this.i18n.text('institution')]]) {
        const control = group.get(key);
        if (control?.invalid) fields.push(`${this.i18n.text('education')} ${String(index + 1).padStart(2, '0')} · ${this.fieldError(control, label)}`);
      }
    });
    return fields;
  }
  removeExperience(index: number): void { this.experiences.removeAt(index); }
  removeEducation(index: number): void { this.educations.removeAt(index); }
  removeCourse(index: number): void { this.courses.removeAt(index); }
  removeProject(index: number): void { this.projects.removeAt(index); }
  formatText(textarea: HTMLTextAreaElement, control: AbstractControl, action: 'bold' | 'italic' | 'bullet' | 'number' | 'clear'): void {
    const value = String(control.value ?? '');
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selected = value.slice(start, end);
    let replacement = selected;
    if (action === 'bold' || action === 'italic') {
      const marker = action === 'bold' ? '**' : '_';
      replacement = `${marker}${selected || (this.i18n.lang() === 'pt' ? 'texto' : 'text')}${marker}`;
    } else if (action === 'bullet' || action === 'number') {
      const lines = (selected || (this.i18n.lang() === 'pt' ? 'Novo item' : 'New item')).split('\n');
      replacement = lines.map((line, index) => action === 'bullet' ? `• ${line.replace(/^\s*(?:[-*•]|\d+\.)\s*/, '')}` : `${index + 1}. ${line.replace(/^\s*(?:[-*•]|\d+\.)\s*/, '')}`).join('\n');
    } else {
      replacement = selected.replace(/\*\*(.*?)\*\*|_(.*?)_/g, '$1$2').replace(/^\s*(?:[-*•]|\d+\.)\s+/gm, '');
    }
    const next = value.slice(0, start) + replacement + value.slice(end);
    control.setValue(next);
    control.markAsDirty();
    requestAnimationFrame(() => { textarea.focus(); textarea.setSelectionRange(start, start + replacement.length); });
  }
  onSkillInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const parts = input.value.split(/[,;\n|]+/);
    this.addSkillTags(parts.slice(0, -1));
    this.skillDraft = parts.at(-1) ?? '';
    input.value = this.skillDraft;
    this.syncSkillsControl();
  }
  onSkillKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === 'Tab' || event.key === ',') {
      if (event.key !== 'Tab' || this.skillDraft.trim()) event.preventDefault();
      this.commitSkillDraft();
    }
  }
  commitSkillDraft(): void {
    this.addSkillTags(this.skillDraft.split(/[,;\n|]+/));
    this.skillDraft = '';
    this.syncSkillsControl();
  }
  removeSkill(skill: string): void {
    this.skillTags = this.skillTags.filter(item => item !== skill);
    this.syncSkillsControl();
  }
  async generatePresentation(): Promise<void> {
    if (this.generatingPresentation) return;
    this.presentationMessage = '';
    this.generatingPresentation = true;
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    try {
      const value = this.form.getRawValue();
      const name = value.name?.trim();
      const headline = value.headline?.trim();
      const experience = value.experience
        .map(row => ({ role: String(row['role'] ?? '').trim(), company: String(row['company'] ?? '').trim() }))
        .filter(item => item.role || item.company)
        .slice(0, 3)
        .map(item => [item.role, item.company].filter(Boolean).join(this.i18n.lang() === 'pt' ? ' na ' : ' at '));
      const education = value.education
        .map(row => [String(row['course'] ?? '').trim(), String(row['institution'] ?? '').trim()].filter(Boolean).join(' — '))
        .filter(Boolean)
        .slice(0, 2);
      const courses = value.courses
        .map(row => [String(row['course'] ?? '').trim(), String(row['institution'] ?? '').trim()].filter(Boolean).join(' — '))
        .filter(Boolean)
        .slice(0, 2);
      const skills = this.skillTags.slice(0, 6);
      const currentSummary = value.summary?.trim() ?? '';
      const narrative = currentSummary === this.lastGeneratedPresentation ? '' : currentSummary;
      const parts: string[] = [];
      if (name || headline) {
        const intro = this.i18n.lang() === 'pt'
          ? `Olá, sou ${name || this.i18n.text('presentationProfessional')}${headline ? `, ${headline}` : ''}.`
          : `Hello, I'm ${name || `a ${this.i18n.text('presentationProfessional')}`}${headline ? `, a ${headline}` : ''}.`;
        parts.push(intro);
      }
      if (narrative) parts.push(narrative);
      if (experience.length) parts.push(this.i18n.lang() === 'pt' ? `Minha experiência inclui ${experience.join('; ')}.` : `My experience includes ${experience.join('; ')}.`);
      if (skills.length) parts.push(this.i18n.lang() === 'pt' ? `Entre minhas principais competências estão ${this.joinNatural(skills, 'pt')}.` : `My core skills include ${this.joinNatural(skills, 'en')}.`);
      if (education.length) parts.push(this.i18n.lang() === 'pt' ? `Minha formação inclui ${this.joinNatural(education, 'pt')}.` : `My education includes ${this.joinNatural(education, 'en')}.`);
      if (courses.length) parts.push(this.i18n.lang() === 'pt' ? `Também tenho cursos em ${this.joinNatural(courses, 'pt')}.` : `I have also completed courses in ${this.joinNatural(courses, 'en')}.`);
      if (!parts.length) {
        this.presentationMessage = this.i18n.text('presentationNeedData');
        return;
      }
      const presentation = parts.join(' ').slice(0, 1500).trimEnd();
      this.form.controls.summary.setValue(presentation);
      this.lastGeneratedPresentation = presentation;
      this.form.controls.summary.markAsDirty();
      this.presentationMessage = this.i18n.text('presentationReady');
    } finally {
      this.generatingPresentation = false;
    }
  }
  async choosePhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    this.photoError = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 8 * 1024 * 1024) {
      this.photoError = this.i18n.text('photoError');
      return;
    }
    try {
      const bitmap = await createImageBitmap(file);
      const scale = Math.min(1, 520 / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(bitmap.width * scale));
      canvas.height = Math.max(1, Math.round(bitmap.height * scale));
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Canvas indisponível');
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      bitmap.close();
      let blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.78));
      if (blob && blob.size > 180_000) blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.58));
      if (!blob || blob.size > 180_000) throw new Error('Imagem muito grande');
      const reader = new FileReader();
      reader.onload = () => this.form.controls.photo.setValue(String(reader.result ?? ''));
      reader.onerror = () => { this.photoError = this.i18n.text('photoError'); };
      reader.readAsDataURL(blob);
    } catch {
      this.photoError = this.i18n.text('photoError');
    }
  }
  removePhoto(): void { this.form.controls.photo.setValue(''); this.photoError = ''; }
  isInvalid(name: string): boolean { const control = this.form.get(name); return !!control && control.invalid && (control.touched || this.submitted); }
  private buildData(): ResumeData {
    const value = this.form.getRawValue();
    return {
      name: value.name?.trim() ?? '', headline: value.headline?.trim() ?? '', email: value.email?.trim() ?? '',
      phone: value.phone?.trim() ?? '', location: value.location?.trim() ?? '', summary: value.summary?.trim() ?? '',
      ...(value.photo ? { photo: value.photo } : {}),
      experience: value.experience.map((row, index) => ({ role: String(row['role'] ?? ''), company: String(row['company'] ?? ''), period: this.periodFor(this.experiences.at(index) as FormGroup), description: String(row['description'] ?? '') })).filter(row => row.role || row.company || row.period || row.description),
      education: value.education.map((row, index) => ({ course: String(row['course'] ?? ''), institution: String(row['institution'] ?? ''), period: this.periodFor(this.educations.at(index) as FormGroup) })).filter(row => row.course || row.institution || row.period),
      courses: value.courses.map(row => ({ course: String(row['course'] ?? ''), institution: String(row['institution'] ?? ''), period: String(row['period'] ?? '') })).filter(row => row.course || row.institution || row.period),
      projects: value.projects.map(row => ({ name: String(row['name'] ?? '').trim(), description: String(row['description'] ?? '').trim(), link: String(row['link'] ?? '').trim() })).filter(row => row.name || row.description || row.link),
      skills: [...this.skillTags], links: (value.links ?? '').split(/\n|,/).map(link => link.trim()).filter(Boolean)
    };
  }
  save(): void {
    if (this.jsonMode) { this.applyJson(); if (this.jsonError) return; }
    this.submitted = true; this.error = false; this.saveErrorMessage = '';
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving = true;
    const value = this.form.getRawValue();
    const data: ResumeData = {
      name: value.name?.trim() ?? '', headline: value.headline?.trim() ?? '', email: value.email?.trim() ?? '',
      phone: value.phone?.trim() ?? '', location: value.location?.trim() ?? '', summary: value.summary?.trim() ?? '',
      ...(value.photo ? { photo: value.photo } : {}),
      experience: value.experience.map((row, index) => ({ role: String(row['role'] ?? ''), company: String(row['company'] ?? ''), period: this.periodFor(this.experiences.at(index) as FormGroup), description: String(row['description'] ?? '') })).filter(row => row.role || row.company || row.period || row.description),
      education: value.education.map((row, index) => ({ course: String(row['course'] ?? ''), institution: String(row['institution'] ?? ''), period: this.periodFor(this.educations.at(index) as FormGroup), })).filter(row => row.course || row.institution || row.period),
      courses: value.courses.map(row => ({ course: String(row['course'] ?? ''), institution: String(row['institution'] ?? ''), period: String(row['period'] ?? '') })).filter(row => row.course || row.institution || row.period),
      projects: value.projects.map(row => ({ name: String(row['name'] ?? '').trim(), description: String(row['description'] ?? '').trim(), link: String(row['link'] ?? '').trim() })).filter(row => row.name || row.description || row.link),
      skills: [...this.skillTags],
      links: (value.links ?? '').split(/\n|,/).map(link => link.trim()).filter(Boolean)
    };
    const title = value.title?.trim() || data.name;
    const request = this.id === null ? this.api.create(title, data, this.selectedLayout) : this.api.update(this.id, title, data, this.selectedLayout);
    request.subscribe({
      next: record => { this.saving = false; void this.router.navigate(['/preview', record.id]); },
      error: response => {
        this.saving = false; this.error = true;
        const backendFields = Array.isArray(response?.error?.fieldErrors) ? response.error.fieldErrors : [];
        const messages = backendFields.map((item: unknown) => {
          if (!item || typeof item !== 'object') return '';
          const detail = item as { field?: unknown; message?: unknown };
          return `${String(detail.field ?? 'Campo')}: ${String(detail.message ?? 'verifique este campo')}`;
        }).filter(Boolean);
        this.saveErrorMessage = messages.length
          ? messages.join(' · ')
          : response?.status >= 500
            ? (this.i18n.lang() === 'pt' ? 'Falha interna ao gravar o currículo. Nenhum campo do formulário foi rejeitado; seus dados continuam preenchidos.' : 'The server failed while saving this resume. No form field was rejected; your data is still in the form.')
            : (this.i18n.lang() === 'pt' ? 'O servidor rejeitou os dados. Confira os campos destacados e tente novamente.' : 'The server rejected the data. Check the highlighted fields and try again.');
      }
    });
  }
  private load(id: number): void {
    this.loading = true; this.error = false; this.notFound = false;
    this.api.get(id).subscribe({
      next: record => { this.selectedLayout = record.layout; this.form.controls.title.setValue(record.title ?? ''); this.applyData(this.api.parse(record)); const draft = window.history.state?.['resumeEditorState'] as { title?: string; data?: ResumeData; layout?: ResumeLayout; jsonMode?: boolean; jsonText?: string } | undefined; if (draft?.data) { this.form.controls.title.setValue(draft.title ?? ''); this.selectedLayout = draft.layout ?? record.layout; this.applyData(draft.data); this.jsonMode = draft.jsonMode ?? false; this.jsonText = draft.jsonText ?? ''; } this.loading = false; },
      error: () => { this.loading = false; this.notFound = true; }
    });
  }
  private applyData(data: ResumeData): void {
    this.lastGeneratedPresentation = '';
    this.presentationMessage = '';
    this.skillTags = this.normalizeSkillTags(data.skills);
    this.skillDraft = '';
    this.form.patchValue({ name: data.name, headline: data.headline, email: data.email, phone: data.phone, location: data.location, summary: data.summary, photo: data.photo ?? '', skills: this.skillTags.join(', '), links: data.links.join('\n') });
    this.experiences.clear(); data.experience.forEach(item => this.addExperience(item));
    this.educations.clear(); data.education.forEach(item => this.addEducation(item));
    this.courses.clear(); (data.courses ?? []).forEach(item => this.addCourse(item));
    this.projects.clear(); (data.projects ?? []).forEach(item => this.addProject(item));
  }
  private addSkillTags(values: string[]): void {
    const known = new Set(this.skillTags.map(skill => skill.toLocaleLowerCase()));
    for (const value of values) {
      const skill = value.trim();
      const key = skill.toLocaleLowerCase();
      if (skill && !known.has(key)) { this.skillTags.push(skill); known.add(key); }
    }
  }
  private normalizeSkillTags(values: string[]): string[] {
    const result: string[] = [];
    const known = new Set<string>();
    for (const value of values.flatMap(item => String(item).split(/[,;\n|•·]+/))) {
      const skill = value.trim();
      const key = skill.toLocaleLowerCase();
      if (skill && !known.has(key)) { result.push(skill); known.add(key); }
    }
    return result;
  }
  private syncSkillsControl(): void { this.form.controls.skills.setValue(this.skillTags.join(', ')); }
  private periodFor(group: FormGroup): string {
    const selected = String(group.get('period')?.value ?? '').trim();
    return selected;
  }
  private parsePeriod(value: string): { start: string; end: string; current: boolean } {
    const parts = value.split(/\s*(?:—|–|−)\s*|\s+(?:-|to|até)\s+/i);
    const end = (parts[1] ?? '').trim().toLocaleLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[.!]+$/, '');
    return { start: this.toMonthValue(parts[0] ?? ''), end: this.toMonthValue(parts[1] ?? ''), current: /^(atual|atualmente|atua[il]\w*|presente|current|currently|present|ongoing|em andamento)$/.test(end) };
  }
  private toMonthValue(value: string): string {
    const text = value.trim();
    const yearMonth = text.match(/^(\d{4})[-/.](\d{1,2})$/);
    if (yearMonth) return `${yearMonth[1]}-${yearMonth[2].padStart(2, '0')}`;
    const monthYear = text.match(/^(\d{1,2})[-/.](\d{4})$/);
    if (monthYear && Number(monthYear[1]) >= 1 && Number(monthYear[1]) <= 12) return `${monthYear[2]}-${monthYear[1].padStart(2, '0')}`;
    const normalized = text.toLocaleLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[.,]/g, '').trim();
    const namedMonth = normalized.match(/^(\D+?)\s+(?:de\s+)?((?:19|20)\d{2})$/) ?? normalized.match(/^((?:19|20)\d{2})\s+(\D+)$/);
    if (namedMonth) {
      const monthToken = (namedMonth[1].match(/^\d{4}$/) ? namedMonth[2] : namedMonth[1]).trim();
      const yearToken = namedMonth[1].match(/^\d{4}$/) ? namedMonth[1] : namedMonth[2];
      const months: Record<string, string> = {
        jan: '01', janeiro: '01', january: '01', feb: '02', fev: '02', fevereiro: '02', february: '02',
        mar: '03', marco: '03', march: '03', apr: '04', abr: '04', abril: '04', april: '04',
        may: '05', mai: '05', maio: '05', jun: '06', junho: '06', june: '06', jul: '07', julho: '07', july: '07',
        aug: '08', ago: '08', agosto: '08', august: '08', sep: '09', set: '09', setembro: '09', september: '09',
        oct: '10', out: '10', outubro: '10', october: '10', nov: '11', novembro: '11', november: '11',
        dec: '12', dez: '12', dezembro: '12', december: '12'
      };
      const month = months[monthToken.replace(/\s+/g, '')];
      if (month) return `${yearToken}-${month}`;
    }
    const year = text.match(/^(\d{4})$/);
    return year ? `${year[1]}-01` : '';
  }
  private formatMonth(value: string): string {
    const match = value.match(/^(\d{4})-(\d{2})$/);
    if (!match) return '';
    return new Date(Number(match[1]), Number(match[2]) - 1, 1).toLocaleDateString(this.i18n.lang() === 'pt' ? 'pt-BR' : 'en', { month: 'short', year: 'numeric' });
  }
  private joinNatural(values: string[], language: 'pt' | 'en'): string {
    if (values.length < 2) return values[0] ?? '';
    const finalJoin = language === 'pt' ? ' e ' : ' and ';
    return `${values.slice(0, -1).join(', ')}${finalJoin}${values.at(-1)}`;
  }
}
