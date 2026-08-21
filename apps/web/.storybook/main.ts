import type { StorybookConfig } from '@storybook/tanstack-react';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(js|jsx|mjs|ts|tsx)'],
  addons: ['@storybook/addon-a11y', '@storybook/addon-docs'],
  framework: '@storybook/tanstack-react',
  viteFinal(viteConfig) {
    return Promise.resolve({
      ...viteConfig,
      build: {
        ...viteConfig.build,
        // Storybook bundles its docs and accessibility runtime into the workbench.
        chunkSizeWarningLimit: 1_500,
      },
    });
  },
};
export default config;
