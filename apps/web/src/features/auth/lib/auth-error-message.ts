import { ApiError } from '@/lib/api/api-client';

export function authErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '操作未完成，请稍后重试。';
  switch (error.code) {
    case 'AUTHENTICATION_FAILED':
      return '用户名或密码错误，或账号当前不可登录。';
    case 'RATE_LIMITED': {
      if (error.retryAfterNs !== undefined) {
        const seconds = (error.retryAfterNs + 999_999_999n) / 1_000_000_000n;
        return `尝试次数过多，请 ${seconds.toString()} 秒后再试。`;
      }
      return '尝试次数过多，请稍后再试。';
    }
    case 'UNAUTHENTICATED':
      return '登录状态已失效，请重新登录。';
    case 'FORBIDDEN':
      return '当前账号无权执行此操作。';
    case 'SERVICE_UNAVAILABLE':
    case 'GATEWAY_TIMEOUT':
      return '身份服务暂时不可用，你可以稍后重试。';
    case 'VALIDATION_FAILED':
      return '请检查填写内容。';
    case 'USER_NAME_CONFLICT':
    case 'USERNAME_CONFLICT':
      return '该用户名已存在。';
    default:
      return error.kind === 'network' || error.kind === 'timeout'
        ? '网络连接异常，请检查后重试。'
        : '操作未完成，请稍后重试。';
  }
}
