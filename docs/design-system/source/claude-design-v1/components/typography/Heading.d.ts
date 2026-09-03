import * as React from "react";

/**
 * Display and heading type. Level maps to the source scale:
 * 1 = 72px/510, 2 = 48px/510, 3 = 32px/400, 4 = 24px/400, 5 = 20px/590.
 *
 * @startingPoint section="Typography" subtitle="Display, heading, body and eyebrow type" viewport="700x260"
 */
export interface HeadingProps {
  level?: 1 | 2 | 3 | 4 | 5;
  /** Override the rendered tag without changing the size. */
  as?: keyof JSX.IntrinsicElements;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Heading(props: HeadingProps): JSX.Element;
