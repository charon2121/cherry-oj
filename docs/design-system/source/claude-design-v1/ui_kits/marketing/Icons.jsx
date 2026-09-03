// Lucide glyph paths, inlined at 1.75 stroke — see readme ICONOGRAPHY (substitution).
const Icon = ({ name, size = 16, color = "currentColor", strokeWidth = 1.75, style }) => {
  const paths = {
    plus: "M12 5v14M5 12h14",
    check: "m20 6-11 11-5-5",
    zap: "M4 14h6l-2 7 10-11h-6l2-7z",
    layers: "m12 3 9 5-9 5-9-5 9-5M3 13l9 5 9-5",
    terminal: "m4 17 6-6-6-6M12 19h8",
    gauge: "M12 14 8.5 9.5M3 12a9 9 0 1 1 18 0",
    arrowRight: "M5 12h14M13 5l7 7-7 7",
    github: "M9 19c-4 1.5-4-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 4.5-1.4 4.5-5a4 4 0 0 0-1.1-2.8 3.7 3.7 0 0 0-.1-2.8s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.6 0C5.3 3.8 4.2 4.1 4.2 4.1a3.7 3.7 0 0 0-.1 2.8A4 4 0 0 0 3 9.7c0 3.6 1.7 4.7 4.5 5-.6.6-.6 1.2-.5 2V20",
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      style={{ flex: "none", ...style }} aria-hidden="true">
      <path d={paths[name] || paths.check} />
      {name === "gauge" ? <circle cx="12" cy="12" r="1" fill={color} stroke="none" /> : null}
    </svg>
  );
};
Object.assign(window, { Icon });
