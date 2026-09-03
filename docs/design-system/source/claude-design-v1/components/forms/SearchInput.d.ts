import * as React from "react";

/** Search / command-palette trigger with an optional keyboard-shortcut chip. */
export interface SearchInputProps {
  placeholder?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  /** e.g. "⌘K" — rendered as a mono chip on the right. */
  shortcut?: string;
  style?: React.CSSProperties;
}

export declare function SearchInput(props: SearchInputProps): JSX.Element;
