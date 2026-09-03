/* @ds-bundle: {"format":4,"namespace":"CherryOJDesignSystem_51433c","components":[{"name":"Button","sourcePath":"components/controls/Button.jsx"},{"name":"IconButton","sourcePath":"components/controls/IconButton.jsx"},{"name":"Pill","sourcePath":"components/controls/Pill.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"SearchInput","sourcePath":"components/forms/SearchInput.jsx"},{"name":"Textarea","sourcePath":"components/forms/Textarea.jsx"},{"name":"Container","sourcePath":"components/layout/Container.jsx"},{"name":"Stack","sourcePath":"components/layout/Stack.jsx"},{"name":"NavBar","sourcePath":"components/navigation/NavBar.jsx"},{"name":"Badge","sourcePath":"components/surfaces/Badge.jsx"},{"name":"Card","sourcePath":"components/surfaces/Card.jsx"},{"name":"Eyebrow","sourcePath":"components/typography/Eyebrow.jsx"},{"name":"Heading","sourcePath":"components/typography/Heading.jsx"},{"name":"Text","sourcePath":"components/typography/Text.jsx"}],"sourceHashes":{"components/controls/Button.jsx":"6f6051666c1c","components/controls/IconButton.jsx":"0238fbdcd970","components/controls/Pill.jsx":"4565c5075f04","components/forms/Input.jsx":"6468b9028640","components/forms/SearchInput.jsx":"7e018ddff9c5","components/forms/Textarea.jsx":"9b08dece1123","components/layout/Container.jsx":"e5bdc9b9a1ed","components/layout/Stack.jsx":"2a549da512ae","components/navigation/NavBar.jsx":"1f7036c3a6db","components/surfaces/Badge.jsx":"bf8b2e0b6961","components/surfaces/Card.jsx":"892acc1d1478","components/typography/Eyebrow.jsx":"0c554174dde0","components/typography/Heading.jsx":"b949f45c4a5c","components/typography/Text.jsx":"9f3a31cf8373","ui_kits/app/AppIcons.jsx":"6dc31b2c72dc","ui_kits/app/CommandPalette.jsx":"4265716b1902","ui_kits/app/ProblemList.jsx":"bef7710773c9","ui_kits/app/ProblemView.jsx":"4d0a24eba031","ui_kits/app/Sidebar.jsx":"4b02f1065ca0","ui_kits/marketing/Features.jsx":"4eceb6a10a98","ui_kits/marketing/Footer.jsx":"02e516af3175","ui_kits/marketing/Hero.jsx":"b7ecc6be04fd","ui_kits/marketing/Icons.jsx":"70c8e3c55e50","ui_kits/marketing/SignupSection.jsx":"6d258f27a6b1"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.CherryOJDesignSystem_51433c = window.CherryOJDesignSystem_51433c || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/controls/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const base = {
  display: "inline-flex",
  alignItems: "center",
  justifyContent: "center",
  gap: "var(--space-2)",
  borderRadius: "var(--radius-sm)",
  fontFamily: "var(--font-display)",
  fontFeatureSettings: 'var(--font-features)',
  lineHeight: 1,
  cursor: "pointer",
  border: "1px solid transparent",
  textDecoration: "none",
  whiteSpace: "nowrap",
  transition: "background-color var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard)"
};
const variants = {
  primary: {
    rest: {
      background: "var(--accent)",
      color: "var(--accent-on)"
    },
    hover: {
      background: "var(--accent-hover)"
    },
    active: {
      background: "var(--accent-active)"
    }
  },
  ghost: {
    rest: {
      background: "rgba(255,255,255,0.02)",
      color: "var(--fg-ghost)",
      borderColor: "var(--border-ghost)"
    },
    hover: {
      background: "rgba(255,255,255,0.05)"
    },
    active: {
      background: "rgba(255,255,255,0.03)"
    }
  },
  subtle: {
    rest: {
      background: "rgba(255,255,255,0.04)",
      color: "var(--fg-2)"
    },
    hover: {
      background: "rgba(255,255,255,0.07)"
    },
    active: {
      background: "rgba(255,255,255,0.05)"
    }
  },
  toolbar: {
    rest: {
      background: "rgba(255,255,255,0.05)",
      color: "var(--meta)",
      borderRadius: "var(--radius-micro)",
      borderColor: "var(--border-soft)",
      boxShadow: "var(--elev-subtle)"
    },
    hover: {
      background: "rgba(255,255,255,0.08)",
      color: "var(--fg-2)"
    },
    active: {
      background: "rgba(255,255,255,0.05)"
    }
  }
};
const sizes = {
  sm: {
    padding: "0 6px",
    height: 24,
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-medium)"
  },
  md: {
    padding: "8px 16px",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)"
  },
  lg: {
    padding: "11px 20px",
    fontSize: "var(--text-15)",
    fontWeight: "var(--weight-medium)"
  }
};
function Button({
  variant = "primary",
  size = "md",
  disabled = false,
  iconLeft,
  iconRight,
  href,
  onClick,
  type = "button",
  style,
  children,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const [down, setDown] = React.useState(false);
  const v = variants[variant] || variants.primary;
  const s = {
    ...base,
    ...sizes[size],
    ...v.rest,
    ...(hover && !disabled ? v.hover : null),
    ...(down && !disabled ? v.active : null),
    ...(disabled ? {
      opacity: 0.4,
      cursor: "not-allowed"
    } : null),
    ...style
  };
  const Tag = href ? "a" : "button";
  return /*#__PURE__*/React.createElement(Tag, _extends({
    href: href,
    type: href ? undefined : type,
    style: s,
    onClick: disabled ? undefined : onClick,
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => {
      setHover(false);
      setDown(false);
    },
    onMouseDown: () => setDown(true),
    onMouseUp: () => setDown(false),
    "aria-disabled": disabled || undefined
  }, rest), iconLeft, children, iconRight);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/controls/Button.jsx", error: String((e && e.message) || e) }); }

// components/controls/IconButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const sizes = {
  sm: 24,
  md: 28,
  lg: 32
};
function IconButton({
  size = "md",
  shape = "circle",
  label,
  active = false,
  disabled = false,
  onClick,
  style,
  children,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const px = sizes[size] || sizes.md;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    "aria-label": label,
    title: label,
    onClick: disabled ? undefined : onClick,
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      width: px,
      height: px,
      display: "inline-flex",
      alignItems: "center",
      justifyContent: "center",
      background: active ? "rgba(255,255,255,0.08)" : hover ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.03)",
      color: active ? "var(--fg)" : "var(--fg-2)",
      border: "1px solid var(--border)",
      borderRadius: shape === "circle" ? "var(--radius-circle)" : "var(--radius-sm)",
      cursor: disabled ? "not-allowed" : "pointer",
      opacity: disabled ? 0.4 : 1,
      padding: 0,
      transition: "background-color var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard)",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { IconButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/controls/IconButton.jsx", error: String((e && e.message) || e) }); }

// components/controls/Pill.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Pill({
  dot,
  dotColor = "var(--accent)",
  selected = false,
  onClick,
  style,
  children,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const interactive = typeof onClick === "function";
  return /*#__PURE__*/React.createElement("span", _extends({
    onClick: onClick,
    role: interactive ? "button" : undefined,
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: "var(--space-1x)",
      padding: dot ? "0 10px 0 8px" : "0 10px 0 5px",
      borderRadius: "var(--radius-pill)",
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-xs)",
      fontWeight: "var(--weight-medium)",
      lineHeight: 1.8,
      color: selected ? "var(--fg)" : "var(--fg-2)",
      background: selected ? "rgba(255,255,255,0.05)" : hover && interactive ? "rgba(255,255,255,0.03)" : "transparent",
      border: "1px solid var(--border-solid)",
      cursor: interactive ? "pointer" : "default",
      transition: "background-color var(--motion-fast) var(--ease-standard)",
      ...style
    }
  }, rest), dot ? /*#__PURE__*/React.createElement("span", {
    style: {
      width: 6,
      height: 6,
      borderRadius: "50%",
      background: dotColor,
      flex: "none"
    }
  }) : null, children);
}
Object.assign(__ds_scope, { Pill });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/controls/Pill.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Input({
  label,
  id,
  type = "text",
  placeholder,
  value,
  onChange,
  hint,
  invalid = false,
  disabled = false,
  style,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const fid = id || `in-${Math.random().toString(36).slice(2, 8)}`;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: "var(--space-2)",
      ...style
    }
  }, label ? /*#__PURE__*/React.createElement("label", {
    htmlFor: fid,
    style: {
      fontSize: "var(--text-sm)",
      fontWeight: "var(--weight-medium)",
      color: "var(--fg-2)",
      fontFeatureSettings: "var(--font-features)"
    }
  }, label) : null, /*#__PURE__*/React.createElement("input", _extends({
    id: fid,
    type: type,
    placeholder: placeholder,
    value: value,
    onChange: onChange,
    disabled: disabled,
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      background: "rgba(255,255,255,0.02)",
      color: "var(--fg-2)",
      border: `1px solid ${invalid ? "var(--danger)" : focus ? "var(--accent)" : "var(--border)"}`,
      borderRadius: "var(--radius-sm)",
      padding: "12px 14px",
      fontFamily: "var(--font-body)",
      fontSize: "var(--text-base)",
      fontFeatureSettings: "var(--font-features)",
      outline: "none",
      opacity: disabled ? 0.5 : 1,
      transition: "border-color var(--motion-fast) var(--ease-standard)"
    }
  }, rest)), hint ? /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--text-cap)",
      color: "var(--meta)",
      letterSpacing: "var(--tracking-caption)"
    }
  }, hint) : null);
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/forms/SearchInput.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function SearchInput({
  placeholder = "Search…",
  value,
  onChange,
  shortcut,
  style,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-2)",
      background: "rgba(255,255,255,0.02)",
      border: `1px solid ${focus ? "var(--accent)" : "var(--border)"}`,
      borderRadius: "var(--radius-sm)",
      padding: "5px 8px",
      transition: "border-color var(--motion-fast) var(--ease-standard)",
      ...style
    }
  }, /*#__PURE__*/React.createElement("svg", {
    width: "14",
    height: "14",
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "var(--meta)",
    strokeWidth: "2",
    strokeLinecap: "round",
    style: {
      flex: "none"
    },
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("circle", {
    cx: "11",
    cy: "11",
    r: "7"
  }), /*#__PURE__*/React.createElement("path", {
    d: "m20 20-3.5-3.5"
  })), /*#__PURE__*/React.createElement("input", _extends({
    type: "search",
    placeholder: placeholder,
    value: value,
    onChange: onChange,
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      flex: 1,
      minWidth: 0,
      background: "transparent",
      border: "none",
      outline: "none",
      color: "var(--fg)",
      fontFamily: "var(--font-body)",
      fontSize: "var(--text-sm)",
      fontFeatureSettings: "var(--font-features)",
      padding: "1px 0"
    }
  }, rest)), shortcut ? /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-mono)",
      fontSize: "var(--text-tiny)",
      color: "var(--meta)",
      border: "1px solid var(--border-soft)",
      borderRadius: "var(--radius-micro)",
      padding: "1px 4px",
      flex: "none"
    }
  }, shortcut) : null);
}
Object.assign(__ds_scope, { SearchInput });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/SearchInput.jsx", error: String((e && e.message) || e) }); }

// components/forms/Textarea.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Textarea({
  label,
  id,
  rows = 4,
  placeholder,
  value,
  onChange,
  disabled = false,
  style,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const fid = id || `ta-${Math.random().toString(36).slice(2, 8)}`;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: "var(--space-2)",
      ...style
    }
  }, label ? /*#__PURE__*/React.createElement("label", {
    htmlFor: fid,
    style: {
      fontSize: "var(--text-sm)",
      fontWeight: "var(--weight-medium)",
      color: "var(--fg-2)",
      fontFeatureSettings: "var(--font-features)"
    }
  }, label) : null, /*#__PURE__*/React.createElement("textarea", _extends({
    id: fid,
    rows: rows,
    placeholder: placeholder,
    value: value,
    onChange: onChange,
    disabled: disabled,
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      background: "rgba(255,255,255,0.02)",
      color: "var(--fg-2)",
      border: `1px solid ${focus ? "var(--accent)" : "var(--border)"}`,
      borderRadius: "var(--radius-sm)",
      padding: "12px 14px",
      resize: "vertical",
      fontFamily: "var(--font-body)",
      fontSize: "var(--text-base)",
      lineHeight: "var(--leading-body)",
      fontFeatureSettings: "var(--font-features)",
      outline: "none",
      opacity: disabled ? 0.5 : 1,
      transition: "border-color var(--motion-fast) var(--ease-standard)"
    }
  }, rest)));
}
Object.assign(__ds_scope, { Textarea });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Textarea.jsx", error: String((e && e.message) || e) }); }

// components/layout/Container.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Container({
  as = "div",
  width = "default",
  style,
  children,
  ...rest
}) {
  const Tag = as;
  const max = width === "narrow" ? "760px" : width === "wide" ? "1440px" : "var(--container-max)";
  return /*#__PURE__*/React.createElement(Tag, _extends({
    style: {
      width: "100%",
      maxWidth: max,
      marginInline: "auto",
      paddingInline: "var(--container-gutter-desktop)",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Container });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/layout/Container.jsx", error: String((e && e.message) || e) }); }

// components/layout/Stack.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const gaps = {
  1: "var(--space-1)",
  2: "var(--space-2)",
  3: "var(--space-3)",
  4: "var(--space-4)",
  6: "var(--space-6)",
  8: "var(--space-8)",
  12: "var(--space-12)"
};
function Stack({
  direction = "column",
  gap = 4,
  align,
  justify,
  wrap = false,
  as = "div",
  style,
  children,
  ...rest
}) {
  const Tag = as;
  return /*#__PURE__*/React.createElement(Tag, _extends({
    style: {
      display: "flex",
      flexDirection: direction,
      gap: gaps[gap] || gaps[4],
      alignItems: align,
      justifyContent: justify,
      flexWrap: wrap ? "wrap" : "nowrap",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Stack });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/layout/Stack.jsx", error: String((e && e.message) || e) }); }

// components/navigation/NavBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function NavBar({
  brand = "Cherry OJ",
  links = [],
  activeHref,
  cta,
  secondary,
  style,
  children
}) {
  return /*#__PURE__*/React.createElement("header", {
    style: {
      position: "sticky",
      top: 0,
      zIndex: 20,
      background: "var(--surface-panel)",
      borderBottom: "1px solid var(--border-soft)",
      height: "var(--header-height)",
      display: "flex",
      alignItems: "center",
      padding: "0 var(--container-gutter-desktop)",
      ...style
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: "100%",
      maxWidth: "var(--container-max)",
      margin: "0 auto",
      display: "flex",
      alignItems: "center",
      gap: "var(--space-6)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-base)",
      fontWeight: "var(--weight-semibold)",
      letterSpacing: "var(--tracking-h3)",
      color: "var(--fg)",
      flex: "none"
    }
  }, brand), /*#__PURE__*/React.createElement("nav", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-5)",
      flex: 1
    }
  }, links.map(l => /*#__PURE__*/React.createElement(NavLink, _extends({
    key: l.href || l.label
  }, l, {
    active: l.href === activeHref
  })))), children, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      flex: "none"
    }
  }, secondary, cta)));
}
function NavLink({
  label,
  href,
  active,
  onClick
}) {
  const [hover, setHover] = React.useState(false);
  return /*#__PURE__*/React.createElement("a", {
    href: href,
    onClick: onClick,
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-cap)",
      fontWeight: "var(--weight-medium)",
      color: active || hover ? "var(--fg)" : "var(--fg-2)",
      textDecoration: "none",
      transition: "color var(--motion-fast) var(--ease-standard)"
    }
  }, label);
}
Object.assign(__ds_scope, { NavBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/NavBar.jsx", error: String((e && e.message) || e) }); }

// components/surfaces/Badge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const tones = {
  neutral: {
    background: "rgba(255,255,255,0.05)",
    color: "var(--fg)",
    border: "1px solid var(--border-soft)"
  },
  accent: {
    background: "var(--accent-tint)",
    color: "var(--accent-bright)",
    border: "1px solid rgba(210,4,45,0.35)"
  },
  success: {
    background: "var(--emerald)",
    color: "var(--fg)",
    border: "1px solid transparent"
  },
  warn: {
    background: "rgba(234,179,8,0.14)",
    color: "var(--warn)",
    border: "1px solid rgba(234,179,8,0.3)"
  }
};
function Badge({
  tone = "neutral",
  style,
  children,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: "var(--space-1)",
      padding: "1px 8px",
      borderRadius: "var(--radius-micro)",
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-tiny)",
      fontWeight: "var(--weight-medium)",
      letterSpacing: "0.02em",
      lineHeight: 1.5,
      ...tones[tone],
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/surfaces/Badge.jsx", error: String((e && e.message) || e) }); }

// components/surfaces/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const pads = {
  sm: "var(--space-4)",
  md: "var(--space-6)",
  lg: "var(--space-8)"
};
const radii = {
  md: "var(--radius-md)",
  lg: "var(--radius-lg)",
  xl: "var(--radius-xl)"
};
function Card({
  padding = "md",
  radius = "md",
  interactive = false,
  elevated = false,
  style,
  children,
  ...rest
}) {
  const [hover, setHover] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", _extends({
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      background: interactive && hover ? "rgba(255,255,255,0.04)" : "rgba(255,255,255,0.02)",
      border: "1px solid var(--border)",
      borderRadius: radii[radius] || radii.md,
      padding: pads[padding] || pads.md,
      boxShadow: elevated ? "var(--elev-raised)" : "none",
      transition: "background-color var(--motion-base) var(--ease-standard)",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/surfaces/Card.jsx", error: String((e && e.message) || e) }); }

// components/typography/Eyebrow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Eyebrow({
  tone = "meta",
  style,
  children,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("p", _extends({
    style: {
      margin: 0,
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-xs)",
      fontWeight: "var(--weight-medium)",
      textTransform: "uppercase",
      letterSpacing: "var(--tracking-eyebrow)",
      color: tone === "accent" ? "var(--accent-bright)" : "var(--meta)",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Eyebrow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/typography/Eyebrow.jsx", error: String((e && e.message) || e) }); }

// components/typography/Heading.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const levels = {
  1: {
    fontSize: "var(--text-5xl)",
    lineHeight: "var(--leading-tight)",
    letterSpacing: "var(--tracking-display-xl)",
    fontWeight: "var(--weight-medium)"
  },
  2: {
    fontSize: "var(--text-3xl)",
    lineHeight: "var(--leading-tight)",
    letterSpacing: "var(--tracking-display)",
    fontWeight: "var(--weight-medium)"
  },
  3: {
    fontSize: "var(--text-2xl)",
    lineHeight: "var(--leading-h1)",
    letterSpacing: "var(--tracking-h1)",
    fontWeight: "var(--weight-regular)"
  },
  4: {
    fontSize: "var(--text-xl)",
    lineHeight: "var(--leading-h2)",
    letterSpacing: "var(--tracking-h2)",
    fontWeight: "var(--weight-regular)"
  },
  5: {
    fontSize: "20px",
    lineHeight: "var(--leading-h2)",
    letterSpacing: "var(--tracking-h3)",
    fontWeight: "var(--weight-semibold)"
  }
};
function Heading({
  level = 2,
  as,
  style,
  children,
  ...rest
}) {
  const Tag = as || (level <= 5 ? `h${Math.min(level, 6)}` : "div");
  return /*#__PURE__*/React.createElement(Tag, _extends({
    style: {
      margin: 0,
      color: "var(--fg)",
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      textWrap: "pretty",
      ...(levels[level] || levels[2]),
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Heading });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/typography/Heading.jsx", error: String((e && e.message) || e) }); }

// components/typography/Text.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const sizes = {
  lg: {
    fontSize: "var(--text-lg)",
    lineHeight: "var(--leading-relaxed)",
    letterSpacing: "var(--tracking-body)"
  },
  base: {
    fontSize: "var(--text-base)",
    lineHeight: "var(--leading-body)"
  },
  md: {
    fontSize: "var(--text-15)",
    lineHeight: "var(--leading-relaxed)",
    letterSpacing: "var(--tracking-body)"
  },
  sm: {
    fontSize: "var(--text-sm)",
    lineHeight: "var(--leading-body)",
    letterSpacing: "-0.013em"
  },
  cap: {
    fontSize: "var(--text-cap)",
    lineHeight: "var(--leading-body)",
    letterSpacing: "var(--tracking-caption)"
  },
  xs: {
    fontSize: "var(--text-xs)",
    lineHeight: "var(--leading-label)"
  }
};
const tones = {
  default: "var(--fg-2)",
  strong: "var(--fg)",
  muted: "var(--muted)",
  meta: "var(--meta)",
  accent: "var(--accent-bright)"
};
const weights = {
  light: "var(--weight-light)",
  regular: "var(--weight-regular)",
  medium: "var(--weight-medium)",
  semibold: "var(--weight-semibold)"
};
function Text({
  size = "base",
  tone = "default",
  weight = "regular",
  mono = false,
  as = "p",
  style,
  children,
  ...rest
}) {
  const Tag = as;
  return /*#__PURE__*/React.createElement(Tag, _extends({
    style: {
      margin: 0,
      color: tones[tone] || tones.default,
      fontFamily: mono ? "var(--font-mono)" : "var(--font-body)",
      fontFeatureSettings: mono ? "normal" : "var(--font-features)",
      fontWeight: weights[weight],
      textWrap: "pretty",
      ...(sizes[size] || sizes.base),
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Text });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/typography/Text.jsx", error: String((e && e.message) || e) }); }

// ui_kits/app/AppIcons.jsx
try { (() => {
// Lucide glyph paths, 1.75 stroke — substitution documented in the root readme.
const AppIcon = ({
  name,
  size = 16,
  color = "currentColor",
  style
}) => {
  const paths = {
    inbox: "M22 12h-6l-2 3h-4l-2-3H2M5 4h14l3 8v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z",
    list: "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01",
    trophy: "M8 21h8M12 17v4M7 4h10v4a5 5 0 0 1-10 0zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3",
    users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 7a4 4 0 1 0 0 .01M22 21v-2a4 4 0 0 0-3-3.9",
    settings: "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a7.6 7.6 0 0 0 .1-1 7.6 7.6 0 0 0-.1-1l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-1.7-1L15 4.5h-4l-.3 2.5a7.6 7.6 0 0 0-1.7 1l-2.4-1-2 3.4L6.6 13a7.6 7.6 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 1.7 1l.3 2.5h4l.3-2.5a7.6 7.6 0 0 0 1.7-1l2.4 1 2-3.4z",
    plus: "M12 5v14M5 12h14",
    play: "m6 4 14 8-14 8z",
    check: "m20 6-11 11-5-5",
    x: "M18 6 6 18M6 6l12 12",
    clock: "M12 7v5l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0",
    chevron: "m9 6 6 6-6 6",
    search: "m20 20-3.5-3.5M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0"
  };
  return /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: color,
    strokeWidth: "1.75",
    strokeLinecap: "round",
    strokeLinejoin: "round",
    style: {
      flex: "none",
      ...style
    },
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("path", {
    d: paths[name] || paths.check
  }));
};
Object.assign(window, {
  AppIcon
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/app/AppIcons.jsx", error: String((e && e.message) || e) }); }

// ui_kits/app/CommandPalette.jsx
try { (() => {
const {
  Text,
  Stack
} = window.CherryOJDesignSystem_51433c;
const ITEMS = [["Go to problem…", "P"], ["Create contest", "C"], ["Rejudge submission", "R"], ["Open standings", "S"], ["Switch language", "L"]];
function CommandPalette({
  open,
  onClose
}) {
  React.useEffect(() => {
    const h = e => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);
  if (!open) return null;
  return /*#__PURE__*/React.createElement("div", {
    onClick: onClose,
    style: {
      position: "fixed",
      inset: 0,
      background: "var(--overlay)",
      display: "flex",
      alignItems: "flex-start",
      justifyContent: "center",
      paddingTop: "14vh",
      zIndex: 40
    }
  }, /*#__PURE__*/React.createElement("div", {
    onClick: e => e.stopPropagation(),
    style: {
      width: 520,
      background: "var(--surface)",
      border: "1px solid var(--border)",
      borderRadius: "var(--radius-lg)",
      boxShadow: "var(--elev-dialog)",
      overflow: "hidden"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-2)",
      padding: "12px 14px",
      borderBottom: "1px solid var(--border-soft)"
    }
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: "search",
    size: 15,
    color: "var(--meta)"
  }), /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    placeholder: "Type a command or search\u2026",
    style: {
      flex: 1,
      background: "transparent",
      border: "none",
      outline: "none",
      color: "var(--fg)",
      fontFamily: "var(--font-body)",
      fontSize: "var(--text-base)",
      fontFeatureSettings: "var(--font-features)"
    }
  })), /*#__PURE__*/React.createElement(Stack, {
    gap: 0,
    style: {
      padding: "6px"
    }
  }, ITEMS.map(([label, key], i) => /*#__PURE__*/React.createElement("div", {
    key: label,
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      padding: "8px 10px",
      borderRadius: "var(--radius-xs)",
      background: i === 0 ? "rgba(255,255,255,0.05)" : "transparent"
    }
  }, /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    weight: "medium",
    tone: i === 0 ? "strong" : "default",
    style: {
      flex: 1
    }
  }, label), /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-mono)",
      fontSize: "var(--text-tiny)",
      color: "var(--meta)",
      border: "1px solid var(--border-soft)",
      borderRadius: "var(--radius-micro)",
      padding: "1px 4px"
    }
  }, key))))));
}
Object.assign(window, {
  CommandPalette
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/app/CommandPalette.jsx", error: String((e && e.message) || e) }); }

// ui_kits/app/ProblemList.jsx
try { (() => {
const {
  Text,
  Pill,
  Badge,
  Button,
  SearchInput,
  IconButton,
  Stack
} = window.CherryOJDesignSystem_51433c;
const ROWS = [{
  id: "CO-1042",
  title: "Segment tree range sum",
  tags: ["data structures", "trees"],
  diff: "Medium",
  rate: "48%",
  state: "solved"
}, {
  id: "CO-1041",
  title: "Knapsack with duplicates",
  tags: ["dp"],
  diff: "Medium",
  rate: "51%",
  state: "attempted"
}, {
  id: "CO-1039",
  title: "Dijkstra on grids",
  tags: ["graphs", "shortest path"],
  diff: "Hard",
  rate: "23%",
  state: "solved"
}, {
  id: "CO-1036",
  title: "Two sum, sorted input",
  tags: ["two pointers"],
  diff: "Easy",
  rate: "82%",
  state: "solved"
}, {
  id: "CO-1030",
  title: "Minimum spanning cactus",
  tags: ["graphs", "mst"],
  diff: "Hard",
  rate: "11%",
  state: "none"
}, {
  id: "CO-1027",
  title: "Longest palindromic run",
  tags: ["strings", "dp"],
  diff: "Medium",
  rate: "44%",
  state: "none"
}];
const DIFF = {
  Easy: "var(--success)",
  Medium: "var(--warn)",
  Hard: "var(--accent-bright)"
};
const STATE_DOT = {
  solved: "var(--success)",
  attempted: "var(--warn)",
  none: "rgba(255,255,255,0.1)"
};
function Row({
  r,
  onOpen
}) {
  const [hover, setHover] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    onClick: () => onOpen(r),
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      padding: "10px 16px",
      borderBottom: "1px solid var(--line-tertiary)",
      background: hover ? "rgba(255,255,255,0.02)" : "transparent",
      cursor: "pointer",
      transition: "background-color var(--motion-fast) var(--ease-standard)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 8,
      height: 8,
      borderRadius: r.state === "none" ? 2 : "50%",
      background: STATE_DOT[r.state],
      flex: "none"
    }
  }), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    mono: true,
    style: {
      width: 68,
      flex: "none"
    }
  }, r.id), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    tone: hover ? "strong" : "default",
    weight: "medium",
    style: {
      flex: 1
    }
  }, r.title), /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 1,
    style: {
      flex: "none"
    }
  }, r.tags.map(t => /*#__PURE__*/React.createElement(Badge, {
    key: t
  }, t))), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    weight: "medium",
    style: {
      width: 64,
      flex: "none",
      color: DIFF[r.diff]
    }
  }, r.diff), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    mono: true,
    style: {
      width: 40,
      flex: "none",
      textAlign: "right"
    }
  }, r.rate));
}
function ProblemList({
  onOpen,
  onPalette
}) {
  const [filter, setFilter] = React.useState("All");
  const filters = ["All", "Unsolved", "Solved", "Contest only"];
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      height: "100%",
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("header", {
    style: {
      height: "var(--header-height)",
      flex: "none",
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      padding: "0 var(--space-4)",
      borderBottom: "1px solid var(--border-soft)"
    }
  }, /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    weight: "medium",
    tone: "strong"
  }, "Problems"), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, "1,284"), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(SearchInput, {
    placeholder: "Search problems\u2026",
    shortcut: "\u2318K",
    style: {
      width: 240
    },
    onFocus: onPalette
  }), /*#__PURE__*/React.createElement(IconButton, {
    label: "New problem",
    shape: "square"
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: "plus",
    size: 14
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "none",
      display: "flex",
      alignItems: "center",
      gap: "var(--space-2)",
      padding: "10px var(--space-4)",
      borderBottom: "1px solid var(--border-soft)"
    }
  }, filters.map(f => /*#__PURE__*/React.createElement(Pill, {
    key: f,
    selected: filter === f,
    onClick: () => setFilter(f)
  }, f)), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "toolbar"
  }, "Difficulty"), /*#__PURE__*/React.createElement(Button, {
    variant: "toolbar"
  }, "Acceptance")), /*#__PURE__*/React.createElement("div", {
    style: {
      overflow: "auto",
      flex: 1
    }
  }, ROWS.filter(r => filter === "Solved" ? r.state === "solved" : filter === "Unsolved" ? r.state !== "solved" : true).map(r => /*#__PURE__*/React.createElement(Row, {
    key: r.id,
    r: r,
    onOpen: onOpen
  }))));
}
Object.assign(window, {
  ProblemList,
  ROWS
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/app/ProblemList.jsx", error: String((e && e.message) || e) }); }

// ui_kits/app/ProblemView.jsx
try { (() => {
const {
  Heading,
  Text,
  Badge,
  Pill,
  Button,
  Card,
  Stack,
  Textarea,
  IconButton
} = window.CherryOJDesignSystem_51433c;
function ProblemView({
  problem,
  onBack
}) {
  const [verdict, setVerdict] = React.useState(null);
  const [running, setRunning] = React.useState(false);
  const submit = () => {
    setRunning(true);
    setVerdict(null);
    setTimeout(() => {
      setRunning(false);
      setVerdict({
        state: "Accepted",
        ms: 42,
        mem: "3.1 MB"
      });
    }, 900);
  };
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      height: "100%",
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("header", {
    style: {
      height: "var(--header-height)",
      flex: "none",
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      padding: "0 var(--space-4)",
      borderBottom: "1px solid var(--border-soft)"
    }
  }, /*#__PURE__*/React.createElement(IconButton, {
    label: "Back",
    shape: "square",
    onClick: onBack
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: "x",
    size: 14
  })), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    mono: true
  }, problem.id), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    weight: "medium",
    tone: "strong"
  }, problem.title), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Pill, {
    dot: true,
    dotColor: "var(--success)"
  }, problem.rate, " accepted"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: submit,
    iconLeft: /*#__PURE__*/React.createElement(AppIcon, {
      name: "play",
      size: 13
    })
  }, running ? "Running…" : "Submit")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1.1fr 1fr",
      gap: 0,
      flex: 1,
      minHeight: 0
    }
  }, /*#__PURE__*/React.createElement("section", {
    style: {
      padding: "var(--space-6)",
      overflow: "auto",
      borderRight: "1px solid var(--border-soft)"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 4
  }, /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 2
  }, problem.tags.map(t => /*#__PURE__*/React.createElement(Badge, {
    key: t
  }, t)), /*#__PURE__*/React.createElement(Badge, {
    tone: "accent"
  }, problem.diff)), /*#__PURE__*/React.createElement(Heading, {
    level: 4
  }, problem.title), /*#__PURE__*/React.createElement(Text, {
    size: "md",
    tone: "muted"
  }, "Given an array of n integers and q queries, report the sum of each query range. Updates arrive interleaved with the queries, so a prefix-sum table will not hold."), /*#__PURE__*/React.createElement(Stack, {
    gap: 2
  }, /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    weight: "medium"
  }, "Constraints"), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    mono: true
  }, "1 \u2264 n, q \u2264 2\xB710^5"), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    mono: true
  }, "|a_i| \u2264 10^9")), /*#__PURE__*/React.createElement(Card, {
    padding: "sm",
    radius: "md"
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 2
  }, /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    weight: "medium"
  }, "Sample input"), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    mono: true,
    style: {
      whiteSpace: "pre"
    }
  }, "5 3\n1 2 3 4 5\nQ 1 3\nU 2 10\nQ 1 3"), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    weight: "medium",
    style: {
      marginTop: 6
    }
  }, "Sample output"), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    mono: true,
    style: {
      whiteSpace: "pre"
    }
  }, "6\n14"))))), /*#__PURE__*/React.createElement("section", {
    style: {
      padding: "var(--space-6)",
      display: "flex",
      flexDirection: "column",
      gap: "var(--space-4)",
      minHeight: 0
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 2,
    align: "center"
  }, /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    weight: "medium"
  }, "Solution \xB7 C++17"), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "toolbar"
  }, "Language"), /*#__PURE__*/React.createElement(Button, {
    variant: "toolbar"
  }, "Reset")), /*#__PURE__*/React.createElement(Textarea, {
    rows: 12,
    defaultValue: "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n  int n, q; cin >> n >> q;\n  // segment tree here\n}",
    style: {
      flex: 1
    }
  }), running ? /*#__PURE__*/React.createElement(Card, {
    padding: "sm"
  }, /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 2,
    align: "center"
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: "clock",
    size: 14,
    color: "var(--warn)"
  }), /*#__PURE__*/React.createElement(Text, {
    size: "sm"
  }, "Running 14 test cases\u2026"))) : verdict ? /*#__PURE__*/React.createElement(Card, {
    padding: "sm",
    style: {
      borderColor: "rgba(39,166,68,0.4)"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 3,
    align: "center"
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: "check",
    size: 14,
    color: "var(--success)"
  }), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    weight: "medium",
    tone: "strong"
  }, verdict.state), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    mono: true
  }, verdict.ms, " ms \xB7 ", verdict.mem), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, "14 / 14 tests"))) : /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, "Submit to run against all 14 test cases."))));
}
Object.assign(window, {
  ProblemView
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/app/ProblemView.jsx", error: String((e && e.message) || e) }); }

// ui_kits/app/Sidebar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const {
  Text,
  Stack
} = window.CherryOJDesignSystem_51433c;
const NAV = [{
  id: "problems",
  label: "Problems",
  icon: "list",
  count: 1284
}, {
  id: "contests",
  label: "Contests",
  icon: "trophy",
  count: 42
}, {
  id: "submissions",
  label: "Submissions",
  icon: "inbox",
  count: 9
}, {
  id: "teams",
  label: "Teams",
  icon: "users"
}];
const SETS = ["Beginner ladder", "Graph theory", "ICPC 2026 prep", "Company sets"];
function SideRow({
  active,
  icon,
  label,
  count,
  onClick
}) {
  const [hover, setHover] = React.useState(false);
  return /*#__PURE__*/React.createElement("button", {
    onClick: onClick,
    onMouseEnter: () => setHover(true),
    onMouseLeave: () => setHover(false),
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-2)",
      width: "100%",
      padding: "5px 8px",
      borderRadius: "var(--radius-xs)",
      border: "1px solid transparent",
      background: active ? "rgba(255,255,255,0.05)" : hover ? "rgba(255,255,255,0.03)" : "transparent",
      color: active ? "var(--fg)" : "var(--fg-2)",
      cursor: "pointer",
      textAlign: "left",
      fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-cap)",
      fontWeight: "var(--weight-medium)",
      transition: "background-color var(--motion-fast) var(--ease-standard)"
    }
  }, /*#__PURE__*/React.createElement(AppIcon, {
    name: icon,
    size: 15,
    color: active ? "var(--fg)" : "var(--muted)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1
    }
  }, label), count != null ? /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--text-micro)",
      color: "var(--meta)"
    }
  }, count) : null);
}
function Sidebar({
  view,
  onNavigate
}) {
  return /*#__PURE__*/React.createElement("aside", {
    style: {
      width: "var(--sidebar-width)",
      flex: "none",
      background: "var(--surface-panel)",
      borderRight: "1px solid var(--border-soft)",
      padding: "var(--space-3)",
      display: "flex",
      flexDirection: "column",
      gap: "var(--space-5)",
      height: "100%"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-2)",
      padding: "2px 6px"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 18,
      height: 18,
      borderRadius: "var(--radius-xs)",
      background: "var(--accent)",
      flex: "none"
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-display)",
      fontSize: "var(--text-sm)",
      fontWeight: "var(--weight-semibold)",
      letterSpacing: "var(--tracking-h3)",
      color: "var(--fg)"
    }
  }, "Cherry OJ")), /*#__PURE__*/React.createElement(Stack, {
    gap: 1
  }, NAV.map(n => /*#__PURE__*/React.createElement(SideRow, _extends({
    key: n.id
  }, n, {
    active: view === n.id,
    onClick: () => onNavigate(n.id)
  })))), /*#__PURE__*/React.createElement(Stack, {
    gap: 1
  }, /*#__PURE__*/React.createElement(Text, {
    size: "xs",
    tone: "meta",
    weight: "medium",
    style: {
      padding: "0 8px",
      textTransform: "uppercase",
      letterSpacing: "0.07em",
      fontSize: "var(--text-micro)"
    }
  }, "Your sets"), SETS.map(s => /*#__PURE__*/React.createElement(SideRow, {
    key: s,
    icon: "chevron",
    label: s
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "auto"
    }
  }, /*#__PURE__*/React.createElement(SideRow, {
    icon: "settings",
    label: "Settings"
  })));
}
Object.assign(window, {
  Sidebar
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/app/Sidebar.jsx", error: String((e && e.message) || e) }); }

// ui_kits/marketing/Features.jsx
try { (() => {
const {
  Heading,
  Text,
  Eyebrow,
  Stack,
  Card,
  Container,
  Badge
} = window.CherryOJDesignSystem_51433c;
const FEATURES = [{
  icon: "zap",
  title: "Judged in milliseconds",
  body: "Sandboxed runners return a verdict before you switch tabs. Time and memory reported per test case."
}, {
  icon: "layers",
  title: "Problem sets that scale",
  body: "Group problems into ladders, courses and contests. Reuse test data across every set."
}, {
  icon: "terminal",
  title: "Seventeen languages",
  body: "C++, Rust, Python, Go and more, each pinned to a versioned toolchain image."
}, {
  icon: "gauge",
  title: "Live standings",
  body: "Penalty-aware scoreboards update as submissions land — no refresh, no polling gap."
}, {
  icon: "check",
  title: "Plain-language verdicts",
  body: "Every failure names the test, the limit it crossed, and what to look at next."
}, {
  icon: "github",
  title: "Import from anywhere",
  body: "Pull statements and test data straight from a repository, or push them via the API."
}];
function Features() {
  return /*#__PURE__*/React.createElement(Container, {
    as: "section",
    style: {
      paddingBlock: "var(--section-y-desktop)",
      borderTop: "1px solid var(--border)"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 3
  }, /*#__PURE__*/React.createElement(Eyebrow, null, "What the platform does"), /*#__PURE__*/React.createElement(Heading, {
    level: 3,
    style: {
      maxWidth: "28ch"
    }
  }, "Darkness as the native medium.")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "repeat(3, 1fr)",
      gap: "var(--space-4)",
      marginBlockStart: "var(--space-6)"
    }
  }, FEATURES.map(f => /*#__PURE__*/React.createElement(Card, {
    key: f.title,
    interactive: true
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 3
  }, /*#__PURE__*/React.createElement(Icon, {
    name: f.icon,
    size: 18,
    color: "var(--accent-bright)"
  }), /*#__PURE__*/React.createElement(Heading, {
    level: 5
  }, f.title), /*#__PURE__*/React.createElement(Text, {
    size: "md",
    tone: "muted"
  }, f.body))))), /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 2,
    wrap: true,
    style: {
      marginBlockStart: "var(--space-6)",
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement(Badge, {
    tone: "accent"
  }, "New"), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    tone: "muted"
  }, "Rejudge queues now report progress per test case.")));
}
Object.assign(window, {
  Features
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/marketing/Features.jsx", error: String((e && e.message) || e) }); }

// ui_kits/marketing/Footer.jsx
try { (() => {
const {
  Text,
  Eyebrow,
  Stack,
  Container
} = window.CherryOJDesignSystem_51433c;
const COLS = [["Product", ["Problems", "Contests", "Standings", "Changelog"]], ["Developers", ["API", "Judge images", "Status", "Import guide"]], ["Company", ["About", "Blog", "Careers", "Contact"]]];
function Footer() {
  return /*#__PURE__*/React.createElement(Container, {
    as: "footer",
    style: {
      paddingBlock: "var(--space-12)",
      borderTop: "1px solid var(--border)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1.4fr repeat(3, 1fr)",
      gap: "var(--space-8)"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 3
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-display)",
      fontSize: "var(--text-base)",
      fontWeight: "var(--weight-semibold)",
      letterSpacing: "var(--tracking-h3)",
      color: "var(--fg)"
    }
  }, "Cherry OJ"), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, "An online judge for teams that grade code all day.")), COLS.map(([title, items]) => /*#__PURE__*/React.createElement(Stack, {
    key: title,
    gap: 2
  }, /*#__PURE__*/React.createElement(Eyebrow, null, title), items.map(i => /*#__PURE__*/React.createElement("a", {
    key: i,
    href: "#",
    style: {
      fontSize: "var(--text-cap)",
      color: "var(--fg-2)",
      letterSpacing: "var(--tracking-caption)"
    }
  }, i))))), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    style: {
      marginBlockStart: "var(--space-8)"
    }
  }, "\xA9 2026 Cherry OJ"));
}
Object.assign(window, {
  Footer
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/marketing/Footer.jsx", error: String((e && e.message) || e) }); }

// ui_kits/marketing/Hero.jsx
try { (() => {
const {
  Button,
  Heading,
  Text,
  Eyebrow,
  Stack,
  Card,
  Container,
  Pill
} = window.CherryOJDesignSystem_51433c;
function Hero({
  onSignup
}) {
  return /*#__PURE__*/React.createElement(Container, {
    as: "section",
    style: {
      paddingBlock: "var(--section-y-desktop)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1.2fr 1fr",
      gap: "var(--space-12)",
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 4
  }, /*#__PURE__*/React.createElement(Eyebrow, null, "Cherry OJ \xB7 release 2.4"), /*#__PURE__*/React.createElement(Heading, {
    level: 2,
    style: {
      maxWidth: "22ch"
    }
  }, "Built for people who ship solutions."), /*#__PURE__*/React.createElement(Text, {
    size: "lg",
    tone: "muted",
    style: {
      maxWidth: "50ch"
    }
  }, "Problems, contests, submissions and standings in one place. Judged in milliseconds, reported in plain language, dark by default."), /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 3,
    style: {
      marginBlockStart: "var(--space-2)"
    }
  }, /*#__PURE__*/React.createElement(Button, {
    onClick: onSignup
  }, "Start solving"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    iconRight: /*#__PURE__*/React.createElement(Icon, {
      name: "arrowRight"
    })
  }, "Read the docs")), /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 2,
    wrap: true,
    style: {
      marginBlockStart: "var(--space-4)"
    }
  }, /*#__PURE__*/React.createElement(Pill, {
    dot: true,
    dotColor: "var(--success)"
  }, "1,284 problems"), /*#__PURE__*/React.createElement(Pill, {
    dot: true,
    dotColor: "var(--accent)"
  }, "42 live contests"), /*#__PURE__*/React.createElement(Pill, null, "17 languages"))), /*#__PURE__*/React.createElement(Card, {
    radius: "lg",
    padding: "md",
    elevated: true
  }, /*#__PURE__*/React.createElement(Eyebrow, {
    style: {
      marginBottom: "var(--space-3)"
    }
  }, "Recent submissions"), /*#__PURE__*/React.createElement(Stack, {
    gap: 2
  }, [["Two Sum · C++17", "Accepted", "var(--success)", "42 ms"], ["Segment tree range sum", "Running", "var(--accent)", "—"], ["Knapsack variants", "Wrong answer", "var(--meta)", "test 14"], ["Dijkstra on grids", "Accepted", "var(--success)", "108 ms"]].map(([name, state, dot, time]) => /*#__PURE__*/React.createElement("div", {
    key: name,
    style: {
      display: "flex",
      alignItems: "center",
      gap: "var(--space-3)",
      padding: "6px 0",
      borderBottom: "1px solid var(--border-soft)"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 8,
      height: 8,
      borderRadius: "50%",
      background: dot,
      flex: "none"
    }
  }), /*#__PURE__*/React.createElement(Text, {
    size: "sm",
    style: {
      flex: 1
    }
  }, name), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, state), /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta",
    mono: true,
    style: {
      width: 56,
      textAlign: "right"
    }
  }, time)))))));
}
Object.assign(window, {
  Hero
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/marketing/Hero.jsx", error: String((e && e.message) || e) }); }

// ui_kits/marketing/Icons.jsx
try { (() => {
// Lucide glyph paths, inlined at 1.75 stroke — see readme ICONOGRAPHY (substitution).
const Icon = ({
  name,
  size = 16,
  color = "currentColor",
  strokeWidth = 1.75,
  style
}) => {
  const paths = {
    plus: "M12 5v14M5 12h14",
    check: "m20 6-11 11-5-5",
    zap: "M4 14h6l-2 7 10-11h-6l2-7z",
    layers: "m12 3 9 5-9 5-9-5 9-5M3 13l9 5 9-5",
    terminal: "m4 17 6-6-6-6M12 19h8",
    gauge: "M12 14 8.5 9.5M3 12a9 9 0 1 1 18 0",
    arrowRight: "M5 12h14M13 5l7 7-7 7",
    github: "M9 19c-4 1.5-4-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 4.5-1.4 4.5-5a4 4 0 0 0-1.1-2.8 3.7 3.7 0 0 0-.1-2.8s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.6 0C5.3 3.8 4.2 4.1 4.2 4.1a3.7 3.7 0 0 0-.1 2.8A4 4 0 0 0 3 9.7c0 3.6 1.7 4.7 4.5 5-.6.6-.6 1.2-.5 2V20"
  };
  return /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: color,
    strokeWidth: strokeWidth,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    style: {
      flex: "none",
      ...style
    },
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("path", {
    d: paths[name] || paths.check
  }), name === "gauge" ? /*#__PURE__*/React.createElement("circle", {
    cx: "12",
    cy: "12",
    r: "1",
    fill: color,
    stroke: "none"
  }) : null);
};
Object.assign(window, {
  Icon
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/marketing/Icons.jsx", error: String((e && e.message) || e) }); }

// ui_kits/marketing/SignupSection.jsx
try { (() => {
const {
  Button,
  Heading,
  Text,
  Eyebrow,
  Stack,
  Input,
  Container
} = window.CherryOJDesignSystem_51433c;
function SignupSection({
  onSignup
}) {
  const [email, setEmail] = React.useState("");
  const [sent, setSent] = React.useState(false);
  return /*#__PURE__*/React.createElement(Container, {
    as: "section",
    style: {
      paddingBlock: "var(--section-y-desktop)",
      borderTop: "1px solid var(--border)"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "grid",
      gridTemplateColumns: "1.2fr 1fr",
      gap: "var(--space-12)",
      alignItems: "start"
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 4
  }, /*#__PURE__*/React.createElement(Eyebrow, null, "Get an account"), /*#__PURE__*/React.createElement(Heading, {
    level: 3
  }, "Inputs on dark surfaces."), /*#__PURE__*/React.createElement(Text, {
    size: "lg",
    tone: "muted",
    style: {
      maxWidth: "44ch"
    }
  }, "A 0.02 white fill on a hairline border. Focus shifts the border to cherry \u2014 no halo, no blue ring.")), /*#__PURE__*/React.createElement("form", {
    style: {
      maxWidth: 400,
      width: "100%"
    },
    onSubmit: e => {
      e.preventDefault();
      setSent(true);
      onSignup && onSignup();
    }
  }, /*#__PURE__*/React.createElement(Stack, {
    gap: 4
  }, /*#__PURE__*/React.createElement(Input, {
    label: "Work email",
    type: "email",
    placeholder: "you@company.com",
    value: email,
    onChange: e => setEmail(e.target.value)
  }), /*#__PURE__*/React.createElement(Stack, {
    direction: "row",
    gap: 3
  }, /*#__PURE__*/React.createElement(Button, {
    type: "submit"
  }, sent ? "Check your inbox" : "Sign up"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    type: "button"
  }, "Learn more")), sent ? /*#__PURE__*/React.createElement(Text, {
    size: "cap",
    tone: "meta"
  }, "We sent a sign-in link to ", email || "your inbox", ".") : null))));
}
Object.assign(window, {
  SignupSection
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/marketing/SignupSection.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.IconButton = __ds_scope.IconButton;

__ds_ns.Pill = __ds_scope.Pill;

__ds_ns.Input = __ds_scope.Input;

__ds_ns.SearchInput = __ds_scope.SearchInput;

__ds_ns.Textarea = __ds_scope.Textarea;

__ds_ns.Container = __ds_scope.Container;

__ds_ns.Stack = __ds_scope.Stack;

__ds_ns.NavBar = __ds_scope.NavBar;

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Eyebrow = __ds_scope.Eyebrow;

__ds_ns.Heading = __ds_scope.Heading;

__ds_ns.Text = __ds_scope.Text;

})();
