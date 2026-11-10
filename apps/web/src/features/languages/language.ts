import { z } from 'zod';

/**
 * 语言编号只核对格式：平台支持哪些语言、显示成什么名字都由服务端决定
 * （contracts/languages.json 与 problem-service 的语言表），前端不维护语言名单。
 */
export const languageIdSchema = z.string().regex(/^[a-z][a-z0-9-]{0,31}$/);

/** 编辑器语法：认得的语言给高亮，其余退回纯文本，新语言不改前端也能用。 */
export type EditorSyntax = 'cpp' | 'plain';

/** C 借用 C++ 的语法高亮：关键字、注释、字符串规则一致，多出来的 C++ 关键字在 C 代码里不会出现。 */
const syntaxByLanguage: Record<string, EditorSyntax> = { c: 'cpp', cpp: 'cpp' };

export function editorSyntax(languageId: string | undefined): EditorSyntax {
  return (languageId && syntaxByLanguage[languageId]) || 'plain';
}

type LanguageOption = { id: string; displayName: string };

/**
 * 题目作答用的语言。调用方将作答范围限定为用户所选的语言，
 * 自测与正式提交共享这份范围，避免各自维护选择状态。
 */
export function problemLanguage<T extends LanguageOption>(problem: {
  allowedLanguages: readonly T[];
}): T | undefined {
  return problem.allowedLanguages[0];
}

/** 按题目开放的语言把编号换成显示名；找不到时直接显示编号，不猜名字。 */
export function languageName(
  problem: { allowedLanguages: readonly LanguageOption[] },
  languageId: string,
): string {
  return problem.allowedLanguages.find((item) => item.id === languageId)?.displayName ?? languageId;
}

// 编辑辅助只给已知语言提供骨架；它不决定平台支持哪些语言。
const driverSkeletons: Record<string, string> = {
  cpp: '#include "solution.cpp"\n\nint main() {\n    // 读入数据，调用 Solution 的方法，输出结果\n}\n',
  c: '#include "solution.c"\n\nint main(void) {\n    // 读入数据，调用用户函数，输出结果\n}\n',
};

export function driverSkeleton(languageId: string): string | undefined {
  return driverSkeletons[languageId];
}
