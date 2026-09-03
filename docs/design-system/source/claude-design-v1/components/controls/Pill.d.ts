import * as React from "react";

/** Filter chip / status tag — fully rounded, transparent, solid #23252a hairline border. */
export interface PillProps {
  /** Show a 6px status dot before the label. */
  dot?: boolean;
  dotColor?: string;
  selected?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Pill(props: PillProps): JSX.Element;
