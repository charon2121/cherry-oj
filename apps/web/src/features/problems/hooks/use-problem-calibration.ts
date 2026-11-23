import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import type { AdminProblem, PublishCheck } from '@/generated/api';
import { apiErrorMessage } from '@/lib/api/api-error-message';

import { calibrate, problemKeys } from '../api/problems-api';

export type CalibrationLimits = { cpuSeconds: string; memoryMib: string; clockSeconds: string };

function secondsToNanoseconds(value: string, optional = false): number | null {
  if (optional && value.trim() === '') return null;
  const converted = Number(value) * 1_000_000_000;
  if (!Number.isFinite(converted) || converted <= 0 || !Number.isSafeInteger(converted)) {
    throw new Error('时间限制必须是可换算为安全整数的正数。');
  }
  return converted;
}

function mebibytesToBytes(value: string): number {
  const converted = Number(value) * 1_048_576;
  if (!Number.isFinite(converted) || converted <= 0 || !Number.isSafeInteger(converted)) {
    throw new Error('内存限制必须是可换算为安全整数的正数。');
  }
  return converted;
}

function nanosecondsToSeconds(value: number | null): string | undefined {
  return value === null ? undefined : String(value / 1_000_000_000);
}

function bytesToMebibytes(value: number | null): string | undefined {
  return value === null ? undefined : String(value / 1_048_576);
}

export function useProblemCalibration(
  problem: AdminProblem,
  publishCheck: PublishCheck | undefined,
  onCalibrated: () => Promise<void>,
) {
  const queryClient = useQueryClient();
  const [references, setReferences] = useState<Record<string, string>>({});
  const [calibrationLanguageId, setCalibrationLanguageId] = useState('');
  const [calibrationKind, setCalibrationKind] = useState<'BASE' | 'OVERRIDE'>('BASE');
  const [limits, setLimits] = useState<CalibrationLimits>({
    cpuSeconds: '1',
    memoryMib: '256',
    clockSeconds: '',
  });
  const isCore = problem.codeMode === 'CORE';
  const language =
    problem.allowedLanguages.find((item) => item.id === calibrationLanguageId) ??
    problem.allowedLanguages[0];
  if (!language) throw new Error('题目语言配置为空。');
  const referenceSource = references[language.id] ?? '';
  const setReferenceSource = (value: string) =>
    setReferences((previous) => ({ ...previous, [language.id]: value }));
  const hasReferenceSource = Object.values(references).some((value) => value.trim().length > 0);

  const calibration = useMutation({
    mutationFn: () => {
      const cpuNs = secondsToNanoseconds(limits.cpuSeconds);
      const clockNs = secondsToNanoseconds(limits.clockSeconds, true);
      if (cpuNs === null) throw new Error('请填写 CPU 时间限制。');
      return calibrate(problem.id, {
        languageId: language.id,
        kind: calibrationKind,
        cpuNs,
        memoryBytes: mebibytesToBytes(limits.memoryMib),
        clockNs,
        referenceSource,
      });
    },
    onSuccess: async (result) => {
      const cpuSeconds = nanosecondsToSeconds(result.cpuNs);
      const memoryMib = bytesToMebibytes(result.memoryBytes);
      const clockSeconds = nanosecondsToSeconds(result.clockNs);
      setLimits((current) => ({
        cpuSeconds: cpuSeconds ?? current.cpuSeconds,
        memoryMib: memoryMib ?? current.memoryMib,
        clockSeconds: clockSeconds ?? '',
      }));
      setReferences((previous) => ({ ...previous, [result.languageId]: '' }));
      await onCalibrated();
      await queryClient.invalidateQueries({ queryKey: problemKeys.limits(problem.id) });
      await queryClient.invalidateQueries({ queryKey: problemKeys.readyLanguages(problem.slug) });
    },
  });

  function selectLanguage(languageId: string) {
    setCalibrationLanguageId(languageId);
    calibration.reset();
  }

  const blockedReason = (unsavedReason: string | undefined) => {
    let calibrationBlocked = unsavedReason;
    if (calibrationBlocked === undefined && !problem.testData)
      calibrationBlocked = '先上传测试数据。';
    const selectedChecks =
      publishCheck?.languages?.find((item) => item.languageId === language.id)?.checks ??
      publishCheck?.checks;
    const unavailable = selectedChecks?.find(
      (item) => (item.code === 'ONLINE_JUDGE_NODE' || item.code === 'LANGUAGE') && !item.passed,
    );
    if (calibrationBlocked === undefined && unavailable) calibrationBlocked = unavailable.message;
    if (calibrationBlocked === undefined && isCore && !language.judgeTemplate?.trim())
      calibrationBlocked = '先保存这门语言的驱动代码。';
    return calibrationBlocked;
  };

  return {
    language,
    kind: calibrationKind,
    limits,
    referenceSource,
    referenceLabel: isCore ? '参考核心代码' : '参考程序',
    hasReferenceSource,
    result: calibration.data,
    isRunning: calibration.isPending,
    resultMessage: calibration.data
      ? `标定状态：${calibration.data.status}。参考源码已从页面内存清除。`
      : undefined,
    errorMessage: calibration.isError
      ? apiErrorMessage(calibration.error, '标定失败，参考程序仍保留在当前页面。')
      : undefined,
    blockedReason,
    selectLanguage,
    setLanguageId: setCalibrationLanguageId,
    setKind: setCalibrationKind,
    setLimits,
    setReferenceSource,
    run: () => calibration.mutate(),
  };
}
