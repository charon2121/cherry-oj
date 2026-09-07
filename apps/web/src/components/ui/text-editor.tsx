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
type TextEditorSize = 'compact' | 'default' | 'code' | 'fill';

const editorHeights: Record<TextEditorSize, string> = {
  fill: '100%',
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
  const composingRef = useRef(false);
  const lastReportedValueRef = useRef(value);
  const settings = useRef(new Compartment());
  const { colorScheme } = useTheme();

  useEffect(() => {
    changeRef.current = onChange;
    blurRef.current = onBlur;
  }, [onBlur, onChange]);

  useEffect(() => {
    if (!hostRef.current) return;

    let compositionFrame: number | undefined;
    const notifyChange = (view: EditorView) => {
      const source = view.state.doc.toString();
      if (source === lastReportedValueRef.current) return;
      lastReportedValueRef.current = source;
      changeRef.current(source);
    };
    const beginComposition = () => {
      composingRef.current = true;
      if (compositionFrame !== undefined) window.cancelAnimationFrame(compositionFrame);
      compositionFrame = undefined;
    };
    const finishComposition = () => {
      if (compositionFrame !== undefined) window.cancelAnimationFrame(compositionFrame);
      // CodeMirror flushes the final DOM mutation after compositionend (on the next
      // animation frame on Android). Report after that flush, not the last candidate.
      compositionFrame = window.requestAnimationFrame(() => {
        compositionFrame = undefined;
        const view = viewRef.current;
        if (!view || view.compositionStarted) return;
        composingRef.current = false;
        notifyChange(view);
      });
    };
    const view = new EditorView({
      parent: hostRef.current,
      doc: initialValueRef.current,
      extensions: [
        basicSetup,
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        syntaxTheme,
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (
            update.docChanged &&
            !applyingExternalValue.current &&
            !composingRef.current &&
            !update.view.compositionStarted
          ) {
            notifyChange(update.view);
          }
        }),
        EditorView.domEventHandlers({
          compositionstart() {
            beginComposition();
            return false;
          },
          compositionend() {
            finishComposition();
            return false;
          },
          blur() {
            blurRef.current?.();
            return false;
          },
        }),
        settings.current.of([]),
      ],
    });
    viewRef.current = view;
    // New Android browsers can deliver IME events to the standard EditContext
    // instead of the content element. Observe that native EventTarget as well.
    const nativeContext: unknown = Reflect.get(view.contentDOM, 'editContext');
    const editContext = nativeContext instanceof EventTarget ? nativeContext : null;
    editContext?.addEventListener('compositionstart', beginComposition);
    editContext?.addEventListener('compositionend', finishComposition);
    return () => {
      if (compositionFrame !== undefined) window.cancelAnimationFrame(compositionFrame);
      editContext?.removeEventListener('compositionstart', beginComposition);
      editContext?.removeEventListener('compositionend', finishComposition);
      composingRef.current = false;
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
    if (!view || composingRef.current || view.compositionStarted) return;
    const currentValue = view.state.doc.toString();
    if (currentValue === value) return;
    applyingExternalValue.current = true;
    try {
      view.dispatch({ changes: { from: 0, to: currentValue.length, insert: value } });
      lastReportedValueRef.current = value;
    } finally {
      applyingExternalValue.current = false;
    }
  }, [value]);

  return (
    <div
      ref={hostRef}
      data-slot="text-editor"
      data-disabled={disabled ? '' : undefined}
      data-invalid={ariaInvalid === true || ariaInvalid === 'true' ? '' : undefined}
      className={cn(
        'focus-within:outline-ring border-border bg-surface-translucent focus-within:border-brand-surface data-disabled:border-border data-disabled:bg-surface-translucent data-invalid:border-danger-border overflow-hidden rounded-sm border focus-within:outline-1 focus-within:outline-offset-0',
        className,
      )}
    />
  );
}

export { TextEditor, type TextEditorLanguage, type TextEditorProps, type TextEditorSize };
