import * as React from "react";

/** Body and metadata type. `size="lg"` is the lead paragraph; `mono` switches to the code face. */
export interface TextProps {
  size?: "lg" | "base" | "md" | "sm" | "cap" | "xs";
  tone?: "default" | "strong" | "muted" | "meta" | "accent";
  weight?: "light" | "regular" | "medium" | "semibold";
  mono?: boolean;
  as?: keyof JSX.IntrinsicElements;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Text(props: TextProps): JSX.Element;
