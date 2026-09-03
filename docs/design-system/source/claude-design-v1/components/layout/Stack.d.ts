import * as React from "react";

/** Flex stack with token-only gaps — the system's single spacing primitive. */
export interface StackProps {
  direction?: "row" | "column";
  /** Spacing token step: 1·2·3·4·6·8·12 → 4…48px. */
  gap?: 1 | 2 | 3 | 4 | 6 | 8 | 12;
  align?: React.CSSProperties["alignItems"];
  justify?: React.CSSProperties["justifyContent"];
  wrap?: boolean;
  as?: keyof JSX.IntrinsicElements;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Stack(props: StackProps): JSX.Element;
