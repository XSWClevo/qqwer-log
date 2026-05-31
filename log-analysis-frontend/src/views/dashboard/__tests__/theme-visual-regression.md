# Dashboard Visual Regression Checklist

- Hero area uses a compact command-center stage, not a marketing landing-page composition.
- Metric cards keep stable height and do not shift on hover or active state.
- Drilldown bars, chips, and insight rows stay inside their containers on desktop and mobile.
- Dark theme uses `var(--macos-*)` tokens and `color-mix(...)`; no hardcoded white panel backgrounds.
- Empty, fallback, and normal states share the same panel rhythm and spacing.
- Insight Board can show trend, severity, error pattern, recent logs, hosts, and apps without blank gaps.
