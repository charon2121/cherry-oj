import * as React from "react";

/**
 * Sticky product header — panel-dark bar, wordmark left, 13px/510 links, actions right.
 *
 * @startingPoint section="Navigation" subtitle="Sticky header with wordmark, links and CTA" viewport="700x150"
 */
export interface NavBarLink {
  label: string;
  href?: string;
  onClick?: (e: React.MouseEvent) => void;
}

export interface NavBarProps {
  /** Wordmark text — there is no logo mark in this system. */
  brand?: string;
  links?: NavBarLink[];
  activeHref?: string;
  /** Primary action node, usually a <Button>. */
  cta?: React.ReactNode;
  /** Secondary action node, usually a ghost <Button>. */
  secondary?: React.ReactNode;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}

export declare function NavBar(props: NavBarProps): JSX.Element;
