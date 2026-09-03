import * as React from "react";

/** Uppercase overline above a heading — 12px/510, 0.08em tracking. */
export interface EyebrowProps {
  tone?: "meta" | "accent";
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Eyebrow(props: EyebrowProps): JSX.Element;
