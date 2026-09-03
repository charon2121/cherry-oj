/// <reference lib="dom" />

import '../src/styles/globals.css';

import type { Preview } from '@storybook/tanstack-react';
import { createElement } from 'react';

import { defaultThemeId, themeRegistry } from '../src/generated/design-system/themes.js';
import { applyTheme, resolveTheme } from '../src/lib/theme/theme-runtime.js';

const preview: Preview = {
  decorators: [
    (Story, context) => {
      const themeId = resolveTheme(context.globals['theme']);
      const canvasElement = context.canvasElement as unknown as {
        ownerDocument: {
          documentElement: Parameters<typeof applyTheme>[0];
        };
      };
      applyTheme(canvasElement.ownerDocument.documentElement, themeId);

      return createElement(
        'div',
        { className: 'text-foreground min-h-screen bg-[var(--ds-canvas)]' },
        createElement(Story),
      );
    },
  ],
  initialGlobals: {
    theme: defaultThemeId,
  },
  globalTypes: {
    theme: {
      description: 'Design-system theme',
      toolbar: {
        icon: 'paintbrush',
        dynamicTitle: true,
        items: themeRegistry.map((theme) => ({
          value: theme.id,
          title: `${theme.label} · ${theme.colorScheme}`,
        })),
      },
    },
  },
  parameters: {
    layout: 'fullscreen',
    a11y: {
      test: 'error',
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

export default preview;
