import { describe, expect, it } from 'vitest';

import type { CustomRunData } from '@/generated/api';

import { caseVerdict, sameTokens } from './sample-check';

function run(status: CustomRunData['status'], text = '', truncated = false): CustomRunData {
  return {
    problemId: '5f16b8c1-9c31-4d46-a2aa-9ba02cf65772',
    languageId: 'cpp',
    status,
    stdout: { text, capturedBytes: text.length, truncated },
    effectiveLimits: { cpuNs: 1, memoryBytes: 1, clockNs: 1 },
  };
}

describe('sample comparison', () => {
  it('ignores how whitespace is laid out, like the judge default', () => {
    expect(sameTokens('3', '3\n')).toBe(true);
    expect(sameTokens('1  2\r\n', '1 2\n')).toBe(true);
    expect(sameTokens('1\n2\n', '1 2')).toBe(true);
    expect(sameTokens('  \n', '')).toBe(true);
  });

  it('fails on different, missing or extra tokens', () => {
    expect(sameTokens('4\n', '3\n')).toBe(false);
    expect(sameTokens('1\n', '1 2\n')).toBe(false);
    expect(sameTokens('12', '1 2')).toBe(false);
    expect(sameTokens('', '3')).toBe(false);
  });

  it('judges only completed runs that have an expected output and a full stdout', () => {
    expect(caseVerdict(run('COMPLETED', '3\n'), '3')).toBe('AC');
    expect(caseVerdict(run('COMPLETED', '4\n'), '3')).toBe('WA');
    expect(caseVerdict(run('COMPLETED', '3\n'), undefined)).toBe('RAN');
    expect(caseVerdict(run('COMPLETED', '3', true), '3')).toBe('UNCHECKED');
    expect(caseVerdict(run('COMPILE_ERROR'), '3')).toBe('CE');
    expect(caseVerdict(run('RUNTIME_ERROR'), '3')).toBe('RE');
    expect(caseVerdict(run('TIME_LIMIT_EXCEEDED'), '3')).toBe('TLE');
  });
});
