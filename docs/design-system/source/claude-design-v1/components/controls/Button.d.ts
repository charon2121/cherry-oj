import * as React from "react";

/**
 * Primary action control. Four variants map 1:1 to the source package's button
 * recipes: primary (cherry fill), ghost (near-transparent + solid dark border),
 * subtle (toolbar fill), toolbar (2px radius micro-button).
 *
 * @startingPoint section="Controls" subtitle="Cherry primary, ghost, subtle and toolbar buttons" viewport="700x150"
 */
export interface ButtonProps {
  variant?: "primary" | "ghost" | "subtle" | "toolbar";
  size?: "sm" | "md" | "lg";
  disabled?: boolean;
  iconLeft?: React.ReactNode;
  iconRight?: React.ReactNode;
  /** Renders an <a> instead of a <button>. */
  href?: string;
  onClick?: (e: React.MouseEvent) => void;
  type?: "button" | "submit" | "reset";
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Button(props: ButtonProps): JSX.Element;
