import * as React from "react";

/**
 * Labelled single-line text field. Focus shifts the border to `--accent` — no halo, no ring.
 *
 * @startingPoint section="Forms" subtitle="Text field, textarea and search on dark surfaces" viewport="700x220"
 */
export interface InputProps {
  label?: string;
  id?: string;
  type?: string;
  placeholder?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  /** Caption below the field. */
  hint?: string;
  invalid?: boolean;
  disabled?: boolean;
  style?: React.CSSProperties;
}

export declare function Input(props: InputProps): JSX.Element;
