export type ResumeLayout = 'ATS' | 'EXECUTIVO' | 'TERMINAL' | 'MODERNO' | 'CRIATIVO' | 'COMPACTO';

export interface ExperienceItem { role: string; company: string; period: string; description: string; }
export interface EducationItem { course: string; institution: string; period: string; }
export interface ProjectItem { name: string; description: string; link: string; }
export interface ResumeData {
  name: string;
  headline: string;
  email: string;
  phone: string;
  location: string;
  summary: string;
  experience: ExperienceItem[];
  education: EducationItem[];
  courses: EducationItem[];
  projects: ProjectItem[];
  skills: string[];
  links: string[];
  photo?: string;
}

export interface ResumeImportResult {
  title: string;
  data: ResumeData;
  warnings: string[];
}
export interface ResumeRecord {
  id: number;
  userId: string;
  title: string;
  jsonData: string;
  layout: ResumeLayout;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}
export interface JobMatchResult {
  companyName?: string;
  matchScore: number;
  level: string;
  summary: string;
  matchingRequirements: { requirement: string; evidence: string }[];
  missingRequirements: { requirement: string; reason: string }[];
  keywords: string[];
  resumeSuggestions: string[];
  interviewQuestions: string[];
  disclaimer: string;
}
export interface JobComparison {
  id: number;
  resumeId: number | null;
  profileLabel: string;
  jobText: string;
  result: JobMatchResult;
  language: 'pt' | 'en';
  createdAt: string;
}
export const EMPTY_RESUME: ResumeData = {
  name: '', headline: '', email: '', phone: '', location: '', summary: '',
  experience: [], education: [], courses: [], projects: [], skills: [], links: []
};
