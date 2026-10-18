/** 前缀行数 = 换行个数：judging-service 把报错里的用户文件行号换算成编辑器行号用的是同一条规则。 */
export function prefixLineCount(prefix: string) {
  return prefix.split('\n').length - 1;
}

export const updatedAtFormat = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric',
  day: 'numeric',
});
