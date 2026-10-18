import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';

import { authKeys } from '@/features/auth/api/session-query';
import { ApiError } from '@/lib/api/api-client';

/** 登录失效时回到登录页，登录后回到原页面（与账号页同一做法）。 */
export function useRedirectWhenSignedOut(error: unknown, returnTo: string) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const unauthenticated = error instanceof ApiError && error.status === 401;
  useEffect(() => {
    if (!unauthenticated) return;
    queryClient.setQueryData(authKeys.session(), { authenticated: false });
    void navigate({ to: '/login', search: { returnTo } });
  }, [navigate, queryClient, returnTo, unauthenticated]);
}
