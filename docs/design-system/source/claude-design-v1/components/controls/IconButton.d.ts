import * as React from "react";

/** Icon-only control — circular by default (Linear-derived chrome), square-6px for toolbars. */
export interface IconButtonProps {
  size?: "sm" | "md" | "lg";
  shape?: "circle" | "square";
  /** Accessible name; also the tooltip. */
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function IconButton(props: IconButtonProps): JSX.Element;
