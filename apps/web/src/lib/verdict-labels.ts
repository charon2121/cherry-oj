import type { SubmissionData } from '@/generated/api';

export const verdictLabels = {
  AC: '通过',
  WA: '答案错误',
  PE: '格式错误',
  CE: '编译错误',
  RE: '运行错误',
  TLE: '超过时间限制',
  MLE: '超过内存限制',
  OLE: '超过输出限制',
  SE: '系统错误',
} satisfies Record<NonNullable<SubmissionData['verdict']>, string>;
