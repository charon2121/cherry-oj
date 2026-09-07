import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { themeRegistry } from '@/generated/design-system/themes';
import { ThemeProvider, useTheme } from '@/lib/theme';

import { CodeEditor } from './code-editor';
import type * as CodeEditorRuntime from './code-editor-runtime';
import type { CodeEditorHandle } from './code-editor-runtime';

type Runtime = typeof CodeEditorRuntime;

const { load } = vi.hoisted(() => ({ load: vi.fn<() => Promise<Runtime>>() }));
vi.mock('./code-editor-loader', () => ({ loadCodeEditor: load }));

function makeHandle() {
  return {
    setValue: vi.fn<CodeEditorHandle['setValue']>(),
    updateSettings: vi.fn<CodeEditorHandle['updateSettings']>(),
    dispose: vi.fn<CodeEditorHandle['dispose']>(),
  };
}

function makeRuntime(handle = makeHandle()) {
  const createCodeEditor = vi.fn<Runtime['createCodeEditor']>().mockResolvedValue(handle);
  return { runtime: { createCodeEditor }, handle };
}

function deferred<T>() {
  let resolve: (value: T) => void = () => undefined;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

function SwitchTheme() {
  const { setTheme, colorScheme } = useTheme();
  return (
    <button
      onClick={() => {
        const other = themeRegistry.find((theme) => theme.colorScheme !== colorScheme);
        if (other) setTheme(other.id);
      }}
    >
      切换主题
    </button>
  );
}

describe('CodeEditor lifecycle', () => {
  beforeEach(() => {
    load.mockReset();
    const values = new Map<string, string>();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        clear: () => values.clear(),
        getItem: (key: string) => values.get(key) ?? null,
        key: (index: number) => [...values.keys()][index] ?? null,
        get length() {
          return values.size;
        },
        removeItem: (key: string) => {
          values.delete(key);
        },
        setItem: (key: string, value: string) => {
          values.set(key, value);
        },
      } satisfies Storage,
    });
  });

  it('loads asynchronously using the latest source and releases its view on unmount', async () => {
    const loading = deferred<Runtime>();
    const { runtime, handle } = makeRuntime();
    load.mockReturnValue(loading.promise);
    const onChange = vi.fn();
    const { rerender, unmount } = render(
      <ThemeProvider>
        <CodeEditor value="old" onChange={onChange} />
      </ThemeProvider>,
    );
    expect(screen.getByRole('status')).toHaveTextContent('正在加载代码编辑器');
    rerender(
      <ThemeProvider>
        <CodeEditor value="latest" onChange={onChange} />
      </ThemeProvider>,
    );
    await act(async () => {
      loading.resolve(runtime);
      await loading.promise;
    });
    expect(runtime.createCodeEditor.mock.calls[0]?.[1]).toBe('latest');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    unmount();
    expect(handle.dispose).toHaveBeenCalledOnce();
  });

  it('updates theme/read-only settings and callbacks without recreating the model', async () => {
    const { runtime, handle } = makeRuntime();
    load.mockResolvedValue(runtime);
    const oldChange = vi.fn();
    const newChange = vi.fn();
    const { rerender } = render(
      <ThemeProvider>
        <SwitchTheme />
        <CodeEditor value="code" onChange={oldChange} />
      </ThemeProvider>,
    );
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
    rerender(
      <ThemeProvider>
        <SwitchTheme />
        <CodeEditor value="code" onChange={newChange} readOnly />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: '切换主题' }));
    expect(runtime.createCodeEditor).toHaveBeenCalledOnce();
    expect(handle.dispose).not.toHaveBeenCalled();
    expect(handle.updateSettings.mock.lastCall?.[0].readOnly).toBe(true);
    runtime.createCodeEditor.mock.calls[0]?.[2]('edited');
    expect(oldChange).not.toHaveBeenCalled();
    expect(newChange).toHaveBeenCalledWith('edited');
  });

  it('keeps caller-owned source across load failures and retries', async () => {
    const { runtime } = makeRuntime();
    load.mockRejectedValueOnce(new Error('chunk unavailable')).mockResolvedValueOnce(runtime);
    const onChange = vi.fn();
    const onLoadError = vi.fn();
    render(
      <ThemeProvider>
        <CodeEditor value="unsaved code" onChange={onChange} onLoadError={onLoadError} />
      </ThemeProvider>,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('当前代码仍保留');
    expect(onLoadError).toHaveBeenCalledOnce();
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '重新加载编辑器' }));
    await waitFor(() => expect(runtime.createCodeEditor).toHaveBeenCalledOnce());
    expect(runtime.createCodeEditor.mock.calls[0]?.[1]).toBe('unsaved code');
  });

  it('does not create an editor after its source owner has unmounted', async () => {
    const loading = deferred<Runtime>();
    const { runtime } = makeRuntime();
    load.mockReturnValue(loading.promise);
    const { unmount } = render(
      <ThemeProvider>
        <CodeEditor value="private source" onChange={vi.fn()} />
      </ThemeProvider>,
    );
    unmount();
    await act(async () => {
      loading.resolve(runtime);
      await loading.promise;
    });
    expect(runtime.createCodeEditor).not.toHaveBeenCalled();
  });

  it('disposes a late editor if the account/page changes while its grammar loads', async () => {
    const creation = deferred<CodeEditorHandle>();
    const { runtime, handle } = makeRuntime();
    runtime.createCodeEditor.mockReturnValue(creation.promise);
    load.mockResolvedValue(runtime);
    const { unmount } = render(
      <ThemeProvider>
        <CodeEditor value="private source" onChange={vi.fn()} />
      </ThemeProvider>,
    );
    await waitFor(() => expect(runtime.createCodeEditor).toHaveBeenCalledOnce());
    unmount();
    await act(async () => {
      creation.resolve(handle);
      await creation.promise;
    });
    expect(handle.dispose).toHaveBeenCalledOnce();
  });
});
