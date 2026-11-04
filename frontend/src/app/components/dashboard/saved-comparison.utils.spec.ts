import { extractCompanyName, savedComparisonCompany, savedComparisonScore } from './saved-comparison.utils';

describe('saved comparison display', () => {
  it('reads the saved score, including zero and the legacy score field', () => {
    expect(savedComparisonScore({ matchScore: 0 })).toBe(0);
    expect(savedComparisonScore({ matchScore: 73, score: 82 })).toBe(73);
    expect(savedComparisonScore({ score: 82 })).toBe(82);
    expect(savedComparisonScore({ score: '82%' })).toBe(82);
  });

  it('does not fabricate a score when it is missing or invalid', () => {
    expect(savedComparisonScore({})).toBeNull();
    expect(savedComparisonScore({ matchScore: null })).toBeNull();
    expect(savedComparisonScore({ matchScore: 101 })).toBeNull();
    expect(savedComparisonScore({ matchScore: 'unknown' })).toBeNull();
  });

  it('preserves a saved company and extracts an explicit company from flattened job text', () => {
    const text = 'Vaga Logo da empresa Example Corp. Example Corp Engineering Manager';
    expect(savedComparisonCompany('Saved Company', text)).toBe('Saved Company');
    expect(savedComparisonCompany('', text)).toBe('Example Corp');
    expect(savedComparisonCompany('', 'A empresa é líder em tecnologia.')).toBe('');
    expect(savedComparisonCompany('', 'Desenvolvedor Android no Brasil')).toBe('');
    expect(extractCompanyName('A empresa é líder em tecnologia.')).toBe('');
    expect(extractCompanyName('Desenvolvedor Android no Brasil')).toBe('');
  });
});
