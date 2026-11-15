import { describe, expect, it } from 'vitest';

import { editorSyntax, sourceFileName } from './language';

describe('language metadata', () => {
  it.each([
    ['c', 'Main.c', 'solution.c'],
    ['cpp', 'Main.cpp', 'solution.cpp'],
    ['pypy', 'Main.py', 'solution.py'],
  ])('exports %s using its ACM and CORE file names', (id, driver, solution) => {
    expect(sourceFileName(id, 'ACM')).toBe(driver);
    expect(sourceFileName(id, 'CORE')).toBe(solution);
  });

  it('keeps unknown languages usable as plain text without guessing a file extension', () => {
    expect(sourceFileName('future-language', 'ACM')).toBe('solution.txt');
    expect(editorSyntax('future-language')).toBe('plain');
    expect(editorSyntax('pypy')).toBe('python');
  });
});
