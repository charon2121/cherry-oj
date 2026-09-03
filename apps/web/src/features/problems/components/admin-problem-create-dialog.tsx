import { useForm } from '@tanstack/react-form';
import { Plus } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { FormField } from '@/components/ui/field';
import { InlineNotice } from '@/components/ui/inline-notice';
import { Input } from '@/components/ui/input';
import { SelectField } from '@/components/ui/select';
import { Text } from '@/components/ui/typography';
import type { ProblemDifficulty } from '@/generated/api';
import { ApiError } from '@/lib/api/api-client';

type CreateValues = {
  slug: string;
  title: string;
  difficulty: ProblemDifficulty;
};

const defaults: CreateValues = { slug: '', title: '', difficulty: 'UNRATED' };
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function createErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '创建失败，请稍后重试。';
  if (error.status === 409) return '这个题目标识已经存在，请更换后重试。';
  const requestSuffix = error.requestId ? ` 请求编号：${error.requestId}` : '';
  return `${error.problem?.detail ?? '创建失败，请稍后重试。'}${requestSuffix}`;
}

function slugErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 409) {
    return '这个题目标识已经存在，请更换后重试。';
  }
  return undefined;
}

export function AdminProblemCreateDialog({
  creating,
  onCreate,
}: {
  creating: boolean;
  onCreate: (values: CreateValues) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const titleInputRef = useRef<HTMLInputElement>(null);
  const [errors, setErrors] = useState<Partial<Record<'slug' | 'title' | 'form', string>>>({});

  useEffect(() => {
    if (open) titleInputRef.current?.focus();
  }, [open]);

  const form = useForm({
    defaultValues: defaults,
    onSubmit: async ({ value }) => {
      const nextErrors: typeof errors = {};
      const title = value.title.trim();
      const slug = value.slug.trim();
      if (!title) nextErrors.title = '请输入题目标题。';
      else if (title.length > 160) nextErrors.title = '标题不能超过 160 个字符。';
      if (!slug) nextErrors.slug = '请输入题目标识。';
      else if (slug.length < 3 || slug.length > 80 || !slugPattern.test(slug)) {
        nextErrors.slug = '请使用 3～80 位小写字母、数字和连字符。';
      }
      if (Object.keys(nextErrors).length > 0) {
        setErrors(nextErrors);
        return;
      }
      try {
        await onCreate({ ...value, title, slug });
        form.reset();
        setErrors({});
        setOpen(false);
      } catch (error) {
        const slugError = slugErrorMessage(error);
        setErrors(slugError ? { slug: slugError } : { form: createErrorMessage(error) });
      }
    },
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && creating) return;
        setOpen(nextOpen);
        if (!nextOpen && !creating) {
          form.reset();
          setErrors({});
        }
      }}
    >
      <DialogTrigger render={<Button />}>
        <Plus aria-hidden="true" />
        新建题目
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建题目草稿</DialogTitle>
          <DialogDescription>
            先建立一个可保存的草稿，再进入工作台完善题面、样例和测试数据。
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-5"
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
        >
          <form.Field name="title">
            {(field) => (
              <FormField
                label="题目标题"
                required
                invalid={Boolean(errors.title)}
                error={errors.title}
              >
                <Input
                  ref={titleInputRef}
                  value={field.state.value}
                  maxLength={160}
                  onChange={(event) => {
                    field.handleChange(event.target.value);
                    setErrors((current) =>
                      Object.fromEntries(
                        Object.entries(current).filter(
                          ([key]) => key !== 'title' && key !== 'form',
                        ),
                      ),
                    );
                  }}
                />
              </FormField>
            )}
          </form.Field>
          <form.Field name="slug">
            {(field) => (
              <FormField
                label="题目标识"
                required
                description="用于题目地址，例如 a-plus-b；创建后仍可在后续版本管理中识别同一道题。"
                invalid={Boolean(errors.slug)}
                error={errors.slug}
              >
                <Input
                  value={field.state.value}
                  spellCheck={false}
                  autoCapitalize="none"
                  minLength={3}
                  maxLength={80}
                  onChange={(event) => {
                    field.handleChange(event.target.value.toLowerCase());
                    setErrors((current) =>
                      Object.fromEntries(
                        Object.entries(current).filter(([key]) => key !== 'slug' && key !== 'form'),
                      ),
                    );
                  }}
                />
              </FormField>
            )}
          </form.Field>
          <form.Field name="difficulty">
            {(field) => (
              <SelectField
                label="初始难度"
                value={field.state.value}
                onValueChange={(value) => field.handleChange(value as ProblemDifficulty)}
                items={[
                  { value: 'UNRATED', label: '暂不评级' },
                  { value: 'EASY', label: '简单' },
                  { value: 'MEDIUM', label: '中等' },
                  { value: 'HARD', label: '困难' },
                ]}
              />
            )}
          </form.Field>
          <InlineNotice variant="info" title="当前配置固定为 ACM / C++">
            <Text size="sm" tone="secondary">
              初始配置：ACM 模式 · C++。当前版本只支持这一组合，因此这里展示事实，不提供无效选项。
            </Text>
          </InlineNotice>
          {errors.form ? (
            <Text className="text-danger" size="sm" role="alert">
              {errors.form}
            </Text>
          ) : null}
          <DialogFooter>
            <DialogClose render={<Button variant="secondary" />} disabled={creating}>
              取消
            </DialogClose>
            <Button type="submit" loading={creating} loadingLabel="正在创建草稿…">
              创建草稿
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
