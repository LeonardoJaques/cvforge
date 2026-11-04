/** Read a saved score without turning missing or invalid data into a real score. */
export function savedComparisonScore(result: unknown): number | null {
  if (!result || typeof result !== 'object') return null;
  const saved = result as Record<string, unknown>;
  for (const value of [saved['matchScore'], saved['score']]) {
    let score: number;
    if (typeof value === 'number') score = value;
    else if (typeof value === 'string' && /^\d+(?:\.\d+)?%?$/.test(value.trim())) {
      score = Number(value.trim().replace(/%$/, ''));
    } else continue;
    if (Number.isFinite(score) && score >= 0 && score <= 100) return score;
  }
  return null;
}

/** Extract a company only when the job text explicitly identifies one. */
export function extractCompanyName(text: string): string {
  const patterns = [
    /\bLogo\s+da\s+empresa\s+([^\r\n.]{1,120})\.(?=\s|$)/iu,
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

export function savedComparisonCompany(company: unknown, jobText: string): string {
  if (typeof company === 'string' && company.trim()) return company.trim();
  const logo = (jobText || '').match(/\bLogo\s+da\s+empresa\s+([^\r\n.]{1,120})\.(?=\s|$)/iu);
  return logo?.[1]?.replace(/\s+/g, ' ').trim() || '';
}
