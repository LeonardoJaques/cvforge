import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { JobComparison, JobMatchResult } from '../../models/resume.model';
import { I18nService } from '../../services/i18n.service';
import { ResumeService } from '../../services/resume.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent saved comparisons', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  const result: JobMatchResult = {
    companyName: 'LawnStarter',
    matchScore: 65,
    level: 'Moderate',
    summary: 'Strong technical leadership with people-management gaps.',
    matchingRequirements: [{
      requirement: 'Technical leadership',
      evidence: 'Solution Architect and Tech Lead experience.'
    }],
    missingRequirements: [{
      requirement: 'Individual growth plans',
      reason: 'No examples of career plans or performance reviews.'
    }],
    keywords: ['AI adoption'],
    resumeSuggestions: ['Add measurable team outcomes.'],
    interviewQuestions: ['How do you develop engineers?'],
    disclaimer: 'Review every suggestion before using it.'
  };

  const comparison: JobComparison = {
    id: 13,
    resumeId: 7,
    profileLabel: 'Leonardo_Jaques_Curriculo',
    jobText: 'Software Engineering Manager at LawnStarter',
    result,
    language: 'pt',
    createdAt: '2026-10-31T13:47:32'
  };

  const labels: Record<string, string> = {
    matchSummary: 'SUMMARY',
    matchingRequirements: 'MATCHING REQUIREMENTS',
    missingRequirements: 'GAPS TO COMPLETE',
    resumeSuggestions: 'RESUME SUGGESTIONS',
    interviewQuestions: 'INTERVIEW QUESTIONS',
    jobKeywords: 'JOB KEYWORDS'
  };

  beforeEach(async () => {
    const resumeService = {
      list: () => of([]),
      listJobComparisons: () => of([comparison]),
      saveJobComparison: () => of({ ...comparison, id: 14 })
    };
    const i18n = {
      text: (key: string) => labels[key] ?? key,
      lang: () => 'en'
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ResumeService, useValue: resumeService },
        { provide: I18nService, useValue: i18n }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('shows the newest saved comparison details when the list loads', () => {
    const card = fixture.nativeElement.querySelector('.comparison-card') as HTMLElement;
    const button = card.querySelector('.comparison-open-button') as HTMLButtonElement;
    const details = card.querySelector('.saved-comparison-details') as HTMLElement;

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(details).not.toBeNull();

    const text = details.textContent ?? '';
    expect(text).toContain('SUMMARY');
    expect(text).toContain('Strong technical leadership with people-management gaps.');
    expect(text).toContain('MATCHING REQUIREMENTS');
    expect(text).toContain('Solution Architect and Tech Lead experience.');
    expect(text).toContain('GAPS TO COMPLETE');
    expect(text).toContain('No examples of career plans or performance reviews.');
    expect(text).toContain('RESUME SUGGESTIONS');
    expect(text).toContain('Add measurable team outcomes.');
  });

  it('allows the saved comparison details to be collapsed and reopened', () => {
    const card = fixture.nativeElement.querySelector('.comparison-card') as HTMLElement;
    const button = card.querySelector('.comparison-open-button') as HTMLButtonElement;

    button.click();
    fixture.detectChanges();
    expect(card.querySelector('.saved-comparison-details')).toBeNull();
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    expect(card.querySelector('.saved-comparison-details')).not.toBeNull();
    expect(button.getAttribute('aria-expanded')).toBe('true');
  });

  it('opens a comparison immediately after it is saved', () => {
    const component = fixture.componentInstance;
    component.jobAnalysis = result;
    component.jobDescription = comparison.jobText;
    component.saveComparison();
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.comparison-card') as HTMLElement;
    const button = card.querySelector('.comparison-open-button') as HTMLButtonElement;
    const details = card.querySelector('.saved-comparison-details') as HTMLElement;

    expect(card.textContent).toContain('SUMMARY');
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(details.textContent).toContain('Strong technical leadership with people-management gaps.');
    expect(details.textContent).toContain('Add measurable team outcomes.');
  });
});
