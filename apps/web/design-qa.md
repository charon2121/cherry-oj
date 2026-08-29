# Login page design QA

## Comparison target

- Source visual truth: `/Users/charon/.codex/generated_images/01a04819-4181-7a00-a119-e03bdec1ffe5/exec-97c59845-145f-4807-ac84-3e84d7fa8dc2.png`
- Browser-rendered implementation: `/private/tmp/cherry-oj-login-qa/implementation-revision-1440x1024.png`
- Same-input comparison: `/private/tmp/cherry-oj-login-qa/side-by-side-revision.png`
- Route and state: `/login`, Cherry Black, default form state.
- Viewport and density: implementation `1440 × 1024` CSS pixels at density 1; source `1487 × 1058` pixels,
  proportionally normalized to the same 1440 × 1024 comparison frame.

The source contains a generated example error while the default implementation does not manufacture an error before a real
submission. The comparison therefore judges the shared page composition, form geometry and right-side artwork in their
equivalent regions; error, pending and long-error layout remain covered by Storybook, component tests and E2E.

## Findings

No actionable P0, P1 or P2 mismatch remains after the user-review correction.

- Fonts and typography: the login hierarchy, weights and line lengths preserve the design-system type contracts. The right-side
  display lettering is now part of a real raster artwork rather than being constrained to the normal UI heading scale.
- Spacing and layout rhythm: at 1440 × 1024 the form starts at the same upper-left region as the source, uses the same wide field
  rhythm, and the artwork occupies the intended right half without colliding with the form. Header and Footer remain unchanged.
- Colors and tokens: product copy, controls and states continue to use semantic tokens. The monochrome artwork uses transparent
  alpha and `mix-blend-difference`, so the same asset remains low-contrast in both design-system themes without a theme branch.
- Image quality and asset fidelity: the previous HTML/CSS line drawing was removed. `public/login-workspace-art.png` is a real,
  transparent raster asset with the source's dominant two-line wordmark, line numbers, code rows, dotted grids and technical
  guides. The final crop and placement are sized for the actual right-column slot and contain no baked checkerboard.
- Copy and content: product copy and the sole login action remain unchanged. The decorative text is exactly `FOCUSED WORKSPACE`.
- Responsive and accessibility: the artwork stays decorative and hidden below the desktop breakpoint. Existing labels, password
  reveal, focus behavior, pending/error announcements, forced-colors and reduced-motion behavior remain intact.

## Comparison history

1. The original implementation was rejected in human review with two P1 findings: the right visual was a small, sparse
   HTML/CSS approximation rather than the selected direction, and `min-height: 100%` plus vertical padding caused unnecessary
   page overflow.
2. The correction replaced the approximation with a transparent raster artwork and changed the login content to a border-box,
   fixed-height grid with zero-minimum descendants. A new E2E assertion requires desktop document height to be no larger than
   the viewport.
3. Asset pass 1 was rejected internally because ImageGen baked a checkerboard into an RGB file. The asset was converted to a
   genuine alpha PNG, contrast was reduced to the source's low-key level, and the crop was remeasured against its rendered slot.
4. Final browser evidence at 1440 × 1024 reports `scrollHeight = innerHeight = 1024` and
   `scrollWidth = innerWidth = 1440`; the Footer bottom is exactly 1024. The same-input comparison shows the selected
   left-form/right-wordmark composition at equivalent scale.

## Interaction and regression evidence

- In-app browser: 1440 × 1024 composition, image crop, page geometry, Footer position and console inspection. No console errors.
- `npm run check`: 28 test files and 100 tests passed, including page component states.
- `npm run build`: production build passed.
- `npm run storybook:build`: default, pending, error, long-error and mobile stories built successfully.
- `npm run test:e2e`: 23/23 passed, including Cherry Black/Pure White, 320px, 200% equivalent width, keyboard,
  forced-colors, reduced-motion, pending/error recovery and the new desktop no-overflow assertion.

## Implementation checklist

- [x] Replace HTML/CSS approximation with a real image asset.
- [x] Match the source's dominant wordmark scale, position and supporting editor texture.
- [x] Remove unnecessary desktop horizontal and vertical overflow.
- [x] Preserve form behavior, shared components, semantic tokens, both themes and responsive behavior.
- [x] Re-run same-input visual comparison and full automated verification.

## Second human review return

- [P1] 页面在较短桌面窗口仍出现滚动条。复测 1280×720 时，旧实现的文档尺寸为 `1312 × 843`，
  超出 `1280 × 720` 视口；溢出来自固定宽度的右侧字景和登录内容对 Shell 中间网格行的最小高度贡献。
- 修正计划：让页面以零显式高度参与 Shell 网格计算、再以 `min-height: 100%` 填满可用主区域；字景同时
  受剩余高度和宽度约束，并为短窗口收紧顶部留白。新增 1280×720 默认及错误状态无双轴溢出断言。

## Second return resolution

- 1280×720 应用内浏览器复测：默认状态 `scrollWidth = innerWidth = 1280`、
  `scrollHeight = innerHeight = 720`，Footer bottom 为 720；登录表单和右侧字景均完整位于主区域。
- 1280×720 自动化复测：默认状态及真实 401 错误提示出现后均为水平、垂直溢出 0。
- 1440×1024 最终实现截图：`/private/tmp/cherry-oj-login-qa/implementation-no-scroll-final-1440x1024.png`；
  同画面对照：`/private/tmp/cherry-oj-login-qa/side-by-side-no-scroll-final.png`。最终构图仍保持图 2 的
  左侧表单、右侧大型低对比字景，未因短窗口修正退回小型装饰。
- 最新 `npm run check`、生产构建与 E2E 23/23 通过；滚动修正未引入主题、认证或响应式回归。

final result: passed
