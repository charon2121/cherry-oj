import * as React from "react";

/** Centred content column — 1200px max with 24px desktop gutters. */
export interface ContainerProps {
  as?: keyof JSX.IntrinsicElements;
  width?: "narrow" | "default" | "wide";
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Container(props: ContainerProps): JSX.Element;
