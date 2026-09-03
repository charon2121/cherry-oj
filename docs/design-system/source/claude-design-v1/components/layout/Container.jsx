import React from "react";

export function Container({ as = "div", width = "default", style, children, ...rest }) {
  const Tag = as;
  const max = width === "narrow" ? "760px" : width === "wide" ? "1440px" : "var(--container-max)";
  return (
    <Tag style={{
      width: "100%", maxWidth: max, marginInline: "auto",
      paddingInline: "var(--container-gutter-desktop)", ...style,
    }} {...rest}>{children}</Tag>
  );
}
