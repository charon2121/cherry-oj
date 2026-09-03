import * as React from "react";

/** 2px-radius inline label for versions, statuses and metadata. */
export interface BadgeProps {
  tone?: "neutral" | "accent" | "success" | "warn";
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Badge(props: BadgeProps): JSX.Element;
