import { describe, expect, test } from 'vitest';

import { safeReturnPath } from './safe-return-path';

describe('safeReturnPath', () => {
  test.each([
    undefined,
    null,
    '',
    'admin/users',
    'https://evil.example/steal',
    '//evil.example/steal',
    '/\\evil.example/steal',
    '/admin\n/users',
  ])('rejects an unsafe return target: %s', (target) => {
    expect(safeReturnPath(target)).toBe('/');
  });

  test.each(['/admin/users?page=2', '/account/password', '/problems#recent'])(
    'keeps a same-origin root-relative target: %s',
    (target) => {
      expect(safeReturnPath(target)).toBe(target);
    },
  );
});
