import { Moon, Sun } from 'lucide-react';
import { useState } from 'react';

import { IconButton } from '@/components/ui/icon-button';
import {
  getThemeDefinition,
  type ThemeId,
  themeRegistry,
  type ThemeSetResult,
  useTheme,
} from '@/lib/theme';

type ThemeOption = Readonly<{ id: string }>;

type ThemeSwitcherViewProps = Readonly<{
  className?: string;
  setTheme: (themeId: ThemeId) => ThemeSetResult;
  themeId: ThemeId;
}>;

function getNextTheme<T extends ThemeOption>(
  registry: readonly T[],
  currentThemeId: string,
): T | null {
  if (registry.length <= 1) return null;

  const currentIndex = registry.findIndex((theme) => theme.id === currentThemeId);
  const nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % registry.length;
  return registry[nextIndex] ?? null;
}

function ThemeSwitcherView({ className, setTheme, themeId }: ThemeSwitcherViewProps) {
  const [announcement, setAnnouncement] = useState('');
  const currentTheme = getThemeDefinition(themeId);
  const nextTheme = getNextTheme(themeRegistry, themeId);
  const targetTheme = nextTheme ?? currentTheme;
  const label =
    nextTheme === null ? `当前仅有 ${currentTheme.label}` : `切换到 ${targetTheme.label}`;
  const TargetIcon = targetTheme.colorScheme === 'light' ? Sun : Moon;

  const switchTheme = () => {
    if (nextTheme === null) return;
    const result = setTheme(nextTheme.id);
    setAnnouncement(
      result.persisted
        ? `已切换到 ${nextTheme.label}`
        : `已切换到 ${nextTheme.label}，但浏览器未能记住选择`,
    );
  };

  return (
    <>
      <IconButton
        className={className}
        label={label}
        size="sm"
        disabled={nextTheme === null}
        onClick={switchTheme}
      >
        <TargetIcon aria-hidden="true" />
      </IconButton>
      <span role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </span>
    </>
  );
}

function ThemeSwitcher({ className }: Readonly<{ className?: string }>) {
  const { setTheme, themeId } = useTheme();
  return (
    <ThemeSwitcherView
      {...(className === undefined ? {} : { className })}
      setTheme={setTheme}
      themeId={themeId}
    />
  );
}

export { getNextTheme, ThemeSwitcher, ThemeSwitcherView, type ThemeSwitcherViewProps };
