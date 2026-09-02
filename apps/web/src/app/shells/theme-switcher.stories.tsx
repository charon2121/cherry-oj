import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { useState } from 'react';

import { type ThemeId, themeRegistry } from '@/lib/theme';

import { ThemeSwitcherView } from './theme-switcher';

const darkTheme = themeRegistry.find((theme) => theme.colorScheme === 'dark');
const lightTheme = themeRegistry.find((theme) => theme.colorScheme === 'light');
if (darkTheme === undefined || lightTheme === undefined) {
  throw new Error('ThemeSwitcher stories require dark and light themes.');
}

function StatefulThemeSwitcher({
  initialThemeId,
  persisted = true,
}: Readonly<{ initialThemeId: ThemeId; persisted?: boolean }>) {
  const [themeId, setThemeId] = useState(initialThemeId);
  return (
    <ThemeSwitcherView
      themeId={themeId}
      setTheme={(nextThemeId) => {
        setThemeId(nextThemeId);
        return { themeId: nextThemeId, persisted };
      }}
    />
  );
}

const meta = {
  title: 'App/Navigation/ThemeSwitcher',
  component: ThemeSwitcherView,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof ThemeSwitcherView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CherryBlackCurrent: Story = {
  args: {
    themeId: darkTheme.id,
    setTheme: (themeId) => ({ themeId, persisted: true }),
  },
  render: () => <StatefulThemeSwitcher initialThemeId={darkTheme.id} />,
};

export const PureWhiteCurrent: Story = {
  args: {
    themeId: lightTheme.id,
    setTheme: (themeId) => ({ themeId, persisted: true }),
  },
  render: () => <StatefulThemeSwitcher initialThemeId={lightTheme.id} />,
};

export const StorageFailure: Story = {
  args: {
    themeId: darkTheme.id,
    setTheme: (themeId) => ({ themeId, persisted: false }),
  },
  render: () => <StatefulThemeSwitcher initialThemeId={darkTheme.id} persisted={false} />,
};

export const At320Pixels: Story = {
  args: {
    themeId: darkTheme.id,
    setTheme: (themeId) => ({ themeId, persisted: true }),
  },
  globals: {
    viewport: 'mobile1',
  },
  render: () => <StatefulThemeSwitcher initialThemeId={darkTheme.id} />,
};
