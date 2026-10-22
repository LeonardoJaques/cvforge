import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ResumeData, ResumeRecord } from '../models/resume.model';
import { ResumeService } from './resume.service';

describe('ResumeService', () => {
  let service: ResumeService;
  let http: HttpTestingController;

  const data: ResumeData = {
    name: 'Alex Martins', headline: 'Product Designer', email: 'alex@example.com', phone: '', location: 'São Paulo',
    summary: 'Design systems and product strategy.', experience: [{ role: 'Designer', company: 'Acme', period: '2022 — now', description: 'Built a design system.' }],
    education: [{ course: 'Design', institution: 'Universidade', period: '2018 — 2021' }], courses: [{ course: 'Acessibilidade', institution: 'Acme', period: '2023' }], projects: [{ name: 'Portfolio', description: 'Case study', link: 'https://example.com' }], skills: ['Research', 'Figma'], links: ['https://example.com']
  };
  const record: ResumeRecord = { id: 7, userId: 'anonimo', title: 'Product · 2025', jsonData: JSON.stringify(data), layout: 'ATS' };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ResumeService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists resumes from the API', () => {
    service.list().subscribe(items => expect(items).toEqual([record]));
    const request = http.expectOne(request => request.url.endsWith('/api/v1/resumes'));
    expect(request.request.method).toBe('GET');
    request.flush([record]);
  });

  it('loads a resume by id', () => {
    service.get(7).subscribe(item => expect(item).toEqual(record));
    const request = http.expectOne(request => request.url.endsWith('/api/v1/resumes/7'));
    expect(request.request.method).toBe('GET');
    request.flush(record);
  });

  it('downloads a generated PDF as a Blob', () => {
    service.downloadPdf(7).subscribe(blob => expect(blob.type).toBe('application/pdf'));
    const request = http.expectOne(request => request.url.endsWith('/api/v1/resumes/7/pdf'));
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
  });

  it('uploads a PDF or JSON file to the import endpoint as multipart form data', () => {
    const file = new File(['{}'], 'resume.json', { type: 'application/json' });
    service.importFile(file).subscribe(result => expect(result.data.name).toBe('Alex'));
    const request = http.expectOne(request => request.url.endsWith('/api/v1/resumes/import'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBeTrue();
    const uploaded = (request.request.body as FormData).get('file') as File;
    expect(uploaded.name).toBe('resume.json');
    expect(uploaded.type).toBe('application/json');
    request.flush({ title: 'resume', data: { name: 'Alex' }, warnings: [] });
  });

  it('creates a resume with serialized content and selected layout', () => {
    service.create('Product · 2025', data, 'EXECUTIVO').subscribe(item => expect(item.id).toBe(7));
    const request = http.expectOne(request => request.url.endsWith('/api/v1/resumes'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ userId: 'anonimo', title: 'Product · 2025', jsonData: JSON.stringify(data), layout: 'EXECUTIVO', createdBy: 'CVForge' });
    request.flush({ ...record, layout: 'EXECUTIVO' });
  });

  it('updates and deletes only the requested resume id', () => {
    service.update(7, 'Updated', data, 'TERMINAL').subscribe();
    const update = http.expectOne(request => request.url.endsWith('/api/v1/resumes/7'));
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ title: 'Updated', jsonData: JSON.stringify(data), layout: 'TERMINAL' });
    update.flush({ ...record, title: 'Updated', layout: 'TERMINAL' });
    service.delete(7).subscribe();
    const remove = http.expectOne(request => request.url.endsWith('/api/v1/resumes/7'));
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
  });

  it('recovers malformed JSON into a safe empty resume', () => {
    expect(service.parse({ ...record, jsonData: '{' })).toEqual({ name: '', headline: '', email: '', phone: '', location: '', summary: '', experience: [], education: [], courses: [], projects: [], skills: [], links: [] });
  });

  it('ignores malformed nested entries instead of breaking a preview', () => {
    const parsed = service.parse({ ...record, jsonData: JSON.stringify({ name: 'Alex', experience: [null, 2, { role: 'Engineer' }], education: ['bad'], skills: [null, 'TypeScript'] }) });
    expect(parsed.experience).toEqual([{ role: 'Engineer', company: '', period: '', description: '' }]);
    expect(parsed.education).toEqual([]);
    expect(parsed.skills).toEqual(['null', 'TypeScript']);
  });

  it('preserves an imported photo data URL when parsing a saved resume', () => {
    const photo = 'data:image/jpeg;base64,Zm9v';
    expect(service.parse({ ...record, jsonData: JSON.stringify({ ...data, photo }) }).photo).toBe(photo);
    expect(service.parse({ ...record, jsonData: JSON.stringify({ ...data, photo: 'https://example.com/photo.jpg' }) }).photo).toBeUndefined();
  });

  it('keeps all imported portfolio projects available to the editor and preview', () => {
    const entries = ['Rio Card', 'HidroGeo (Open Source)', 'Credit Application System', 'DDD Project Fullstack', 'Lab Java Quarkus'].map(name => ({ name, description: `${name} description`, link: '' }));
    const parsed = service.parse({ ...record, jsonData: JSON.stringify({ projects: entries }) });
    expect(parsed.projects.map(project => project.name)).toEqual(entries.map(project => project.name));
    expect(parsed.projects).toEqual(entries);
  });

  it('normalizes initials from a name or title', () => {
    expect(service.initials('  alex   martins  silva ')).toBe('AM');
    expect(service.initials('')).toBe('CV');
  });
});
