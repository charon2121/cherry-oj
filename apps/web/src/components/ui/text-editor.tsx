import { cpp } from '@codemirror/lang-cpp';
import { markdown } from '@codemirror/lang-markdown';
import { defaultHighlightStyle, HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { Compartment, EditorState, type Extension } from '@codemirror/state';
import { EditorView, placeholder as placeholderExtension } from '@codemirror/view';
import { tags } from '@lezer/highlight';
import { basicSetup } from 'codemirror';
import { type AriaAttributes, useEffect, useId, useRef } from 'react';

import { useTheme } from '@/lib/theme';
import { cn } from '@/lib/utils';

type TextEditorLanguage = 'plain' | 'markdown' | 'cpp';
type TextEditorSize = 'compact' | 'default' | 'code';

const editorHeights: Record<TextEditorSize, string> = {
  compact: '12rem',
  default: '24rem',
  code: '22rem',
};

const syntaxTheme = syntaxHighlighting(
  HighlightStyle.define([
    { tag: [tags.heading, tags.keyword, tags.typeName], color: 'var(--ds-brand-foreground)' },
    { tag: [tags.string, tags.inserted], color: 'var(--ds-success-foreground)' },
    { tag: [tags.number, tags.bool, tags.atom], color: 'var(--ds-special-foreground)' },
    { tag: [tags.comment, tags.meta], color: 'var(--ds-fg-muted)', fontStyle: 'italic' },
    { tag: [tags.invalid, tags.deleted], color: 'var(--ds-danger-foreground)' },
    { tag: [tags.link, tags.url], color: 'var(--ds-info-foreground)', textDecoration: 'underline' },
  ]),
);

function languageExtension(language: TextEditorLanguage): Extension {
  if (language === 'markdown') return markdown();
  if (language === 'cpp') return cpp();
  return [];
}

function editorTheme(height: string): Extension {
  return EditorView.theme({
    '&': {
      height,
      backgroundColor: 'var(--ds-surface-translucent)',
      color: 'var(--ds-fg)',
      fontSize: 'var(--ds-text-sm)',
    },
    '&.cm-focused': { outline: 'none' },
    '.cm-scroller': {
      overflow: 'auto',
      fontFamily: 'var(--ds-font-mono)',
      lineHeight: 'var(--ds-leading-body)',
    },
    '.cm-content': { caretColor: 'var(--ds-focus)', paddingBlock: 'var(--ds-space-3)' },
    '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--ds-focus)' },
    '.cm-selectionBackground, ::selection': {
      backgroundColor: 'var(--ds-selection-surface) !important',
      color: 'var(--ds-selection-foreground)',
    },
    '.cm-activeLine, .cm-activeLineGutter': {
      backgroundColor: 'var(--ds-surface-translucent-hover)',
    },
    '.cm-gutters': {
      backgroundColor: 'var(--ds-panel)',
      color: 'var(--ds-fg-muted)',
      borderRight: '1px solid var(--ds-border)',
    },
    '.cm-panels': {
      backgroundColor: 'var(--ds-surface-raised)',
      color: 'var(--ds-fg)',
    },
    '.cm-panels.cm-panels-top': { borderBottom: '1px solid var(--ds-border)' },
    '.cm-panels.cm-panels-bottom': { borderTop: '1px solid var(--ds-border)' },
    '.cm-textfield': {
      backgroundColor: 'var(--ds-surface)',
      color: 'var(--ds-fg)',
      border: '1px solid var(--ds-border-strong)',
    },
    '.cm-button': {
      backgroundImage: 'none',
      backgroundColor: 'var(--ds-surface-subtle)',
      color: 'var(--ds-fg)',
      border: '1px solid var(--ds-border-strong)',
    },
    '.cm-tooltip': {
      backgroundColor: 'var(--ds-surface-raised)',
      color: 'var(--ds-fg)',
      border: '1px solid var(--ds-border-strong)',
    },
    '.cm-placeholder': { color: 'var(--ds-fg-muted)' },
  });
}

type TextEditorProps = Readonly<{
  value: string;
  onChange: (value: string) => void;
  language?: TextEditorLanguage;
  size?: TextEditorSize;
  placeholder?: string;
  className?: string;
  id?: string;
  disabled?: boolean;
  readOnly?: boolean;
  required?: boolean;
  onBlur?: () => void;
  'aria-label'?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: AriaAttributes['aria-invalid'];
}>;

function TextEditor({
  value,
  onChange,
  language = 'plain',
  size = 'default',
  placeholder,
  className,
  id,
  disabled = false,
  readOnly = false,
  required = false,
  onBlur,
  'aria-label': ariaLabel,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
}: TextEditorProps) {
  const generatedId = useId();
  const contentId = id ?? `text-editor-${generatedId}`;
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | undefined>(undefined);
  const initialValueRef = useRef(value);
  const changeRef = useRef(onChange);
  const blurRef = useRef(onBlur);
  const applyingExternalValue = useRef(false);
  const settings = useRef(new Compartment());
  const { colorScheme } = useTheme();

  useEffect(() => {
    changeRef.current = onChange;
    blurRef.current = onBlur;
  }, [onBlur, onChange]);

  useEffect(() => {
    if (!hostRef.current) return;

    const view = new EditorView({
      parent: hostRef.current,
      doc: initialValueRef.current,
      extensions: [
        basicSetup,
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        syntaxTheme,
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !applyingExternalValue.current) {
            changeRef.current(update.state.doc.toString());
          }
        }),
        EditorView.domEventHandlers({
          blur() {
            blurRef.current?.();
            return false;
          },
        }),
        settings.current.of([]),
      ],
    });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = undefined;
    };
  }, []);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const attributes: Record<string, string> = {
      id: contentId,
      'aria-label': ariaLabel ?? '文本编辑器',
      'aria-required': String(required),
    };
    if (ariaDescribedBy) attributes['aria-describedby'] = ariaDescribedBy;
    if (ariaInvalid === true || ariaInvalid === 'true') attributes['aria-invalid'] = 'true';
    if (disabled) attributes['aria-disabled'] = 'true';

    const extensions: Extension[] = [
      EditorView.contentAttributes.of(attributes),
      EditorState.readOnly.of(readOnly || disabled),
      EditorView.editable.of(!readOnly && !disabled),
      languageExtension(language),
      EditorView.darkTheme.of(colorScheme === 'dark'),
      editorTheme(editorHeights[size]),
    ];
    if (placeholder) extensions.push(placeholderExtension(placeholder));
    view.dispatch({ effects: settings.current.reconfigure(extensions) });
  }, [
    ariaDescribedBy,
    ariaInvalid,
    ariaLabel,
    colorScheme,
    contentId,
    disabled,
    language,
    placeholder,
    readOnly,
    required,
    size,
  ]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const currentValue = view.state.doc.toString();
    if (currentValue === value) return;
    applyingExternalValue.current = true;
    view.dispatch({ changes: { from: 0, to: currentValue.length, insert: value } });
    applyingExternalValue.current = false;
  }, [value]);

  return (
    <div
      ref={hostRef}
      data-slot="text-editor"
      data-disabled={disabled ? '' : undefined}
      data-invalid={ariaInvalid === true || ariaInvalid === 'true' ? '' : undefined}
      className={cn(
        'focus-within:outline-ring overflow-hidden rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-surface-translucent)] focus-within:border-[var(--ds-brand-surface)] focus-within:outline-2 focus-within:outline-offset-2 data-disabled:border-[var(--ds-border)] data-disabled:bg-[var(--ds-surface-translucent)] data-invalid:border-[var(--ds-danger-border)]',
        className,
      )}
    />
  );
}

export { TextEditor, type TextEditorLanguage, type TextEditorProps, type TextEditorSize };
