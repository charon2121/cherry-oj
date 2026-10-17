import type { CustomRunData, SubmissionData } from '@/generated/api';

/**
 * 一组自测输入的结论：判定码与正式提交同一套（AC、WA、CE…），名称也就同一套。
 * RAN：没有预期输出（自定义输入），只说运行完成；UNCHECKED：输出被截断，无法比对。
 */
export type CaseVerdict = NonNullable<SubmissionData['verdict']> | 'RAN' | 'UNCHECKED';

/** 按空白切词逐个比对，与判题机的默认规则（strictWhitespace=false）一致：只看词，不看空格和换行怎么排。 */
export function sameTokens(actual: string, expected: string) {
  const got = actual.split(/\s+/).filter(Boolean);
  const want = expected.split(/\s+/).filter(Boolean);
  return got.length === want.length && got.every((token, index) => token === want[index]);
}

export function caseVerdict(value: CustomRunData, expected: string | undefined): CaseVerdict {
  switch (value.status) {
    case 'COMPILE_ERROR':
      return 'CE';
    case 'RUNTIME_ERROR':
      return 'RE';
    case 'TIME_LIMIT_EXCEEDED':
      return 'TLE';
    case 'MEMORY_LIMIT_EXCEEDED':
      return 'MLE';
    case 'OUTPUT_LIMIT_EXCEEDED':
      return 'OLE';
    case 'COMPLETED':
      if (expected === undefined) return 'RAN';
      if (value.stdout?.truncated) return 'UNCHECKED';
      return sameTokens(value.stdout?.text ?? '', expected) ? 'AC' : 'WA';
  }
}
