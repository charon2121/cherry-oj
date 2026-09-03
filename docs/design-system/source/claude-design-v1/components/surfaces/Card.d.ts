import * as React from "react";

/**
 * Translucent container — never a solid fill. 0.02 white over the dark canvas,
 * hairline `--border`, 8px radius by default.
 *
 * @startingPoint section="Surfaces" subtitle="Translucent card and badge recipes" viewport="700x200"
 */
export interface CardProps {
  padding?: "sm" | "md" | "lg";
  radius?: "md" | "lg" | "xl";
  /** Lightens the fill to 0.04 on hover. */
  interactive?: boolean;
  elevated?: boolean;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function Card(props: CardProps): JSX.Element;
