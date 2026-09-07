import type { Meta, StoryObj } from '@storybook/tanstack-react';
import { type ReactNode, useEffect, useState } from 'react';

import type { ThemeId } from '@/generated/design-system/themes';
import { ThemeProvider, useTheme } from '@/lib/theme';
import { resolveTheme } from '@/lib/theme/theme-runtime';

import { Button } from './button';
import { CodeEditor } from './code-editor';

const sample =
  '#include <iostream>\nusing namespace std;\n\nint main() {\n    long long a, b;\n    cin >> a >> b;\n    cout << a + b << "\\n";\n    return 0;\n}\n';

function StoryTheme({ themeId, children }: Readonly<{ themeId: ThemeId; children: ReactNode }>) {
  const { setTheme } = useTheme();
  useEffect(() => {
    setTheme(themeId);
  }, [setTheme, themeId]);
  return children;
}

const meta = {
  title: 'UI/CodeEditor',
  component: CodeEditor,
  args: { value: sample, onChange: () => undefined, 'aria-label': 'C++ 代码编辑器' },
  decorators: [
    (Story, context) => (
      <ThemeProvider>
        <StoryTheme themeId={resolveTheme(context.globals['theme'])}>
          <div className="flex max-w-4xl flex-col gap-3 p-6">
            <Story />
          </div>
        </StoryTheme>
      </ThemeProvider>
    ),
  ],
} satisfies Meta<typeof CodeEditor>;

export default meta;
type Story = StoryObj<typeof meta>;

function ControlledEditor({ readOnly = false }: Readonly<{ readOnly?: boolean }>) {
  const [value, setValue] = useState(sample);
  return (
    <>
      <h2 className="text-foreground text-sm">C++ 代码</h2>
      <CodeEditor value={value} onChange={setValue} readOnly={readOnly} className="h-96" />
      <Button variant="secondary" onClick={() => setValue(sample)}>
        恢复示例代码
      </Button>
    </>
  );
}

export const Default: Story = { render: () => <ControlledEditor /> };
export const ReadOnly: Story = { render: () => <ControlledEditor readOnly /> };
