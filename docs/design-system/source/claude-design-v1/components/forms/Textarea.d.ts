import * as React from "react";

/** Multi-line field — same surface recipe as Input, vertical resize only. */
export interface TextareaProps {
  label?: string;
  id?: string;
  rows?: number;
  placeholder?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  disabled?: boolean;
  style?: React.CSSProperties;
}

export declare function Textarea(props: TextareaProps): JSX.Element;
