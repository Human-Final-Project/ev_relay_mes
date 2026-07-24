import { createGlobalStyle } from "styled-components";

// [추가 7월 10일 22:05] DESIGN.md(Industrial Precision Light)의 색상 토큰을 CSS 변수로 등록
// [추가 7월 10일 22:05] DESIGN.md의 typography, spacing, rounded 토큰도 함께 CSS 변수로 등록
const GlobalStyle = createGlobalStyle`
  :root {
    /* [추가 7월 10일 22:05] Colors - DESIGN.md 그대로 반영 */
    --color-surface: #f9f9ff;
    --color-surface-dim: #cfdaf2;
    --color-surface-bright: #f9f9ff;
    --color-surface-container-lowest: #ffffff;
    --color-surface-container-low: #f0f3ff;
    --color-surface-container: #e7eeff;
    --color-surface-container-high: #dee8ff;
    --color-surface-container-highest: #d8e3fb;
    --color-on-surface: #111c2d;
    --color-on-surface-variant: #3e4850;
    --color-inverse-surface: #263143;
    --color-inverse-on-surface: #ecf1ff;
    --color-outline: #6e7881;
    --color-outline-variant: #bec8d2;
    --color-surface-tint: #006591;
    --color-primary: #006591;
    --color-on-primary: #ffffff;
    --color-primary-container: #0ea5e9;
    --color-on-primary-container: #003751;
    --color-inverse-primary: #89ceff;
    --color-secondary: #505f76;
    --color-on-secondary: #ffffff;
    --color-secondary-container: #d0e1fb;
    --color-on-secondary-container: #54647a;
    --color-tertiary: #5a5f62;
    --color-on-tertiary: #ffffff;
    --color-tertiary-container: #979ca0;
    --color-on-tertiary-container: #2f3437;
    --color-error: #ba1a1a;
    --color-on-error: #ffffff;
    --color-error-container: #ffdad6;
    --color-on-error-container: #93000a;
    --color-background: #f9f9ff;
    --color-on-background: #111c2d;
    --color-surface-variant: #d8e3fb;

    /* [추가 7월 10일 22:05] Typography - DESIGN.md 스케일 그대로 반영 */
    --font-family-base: "Inter", sans-serif;
    --font-family-code: "JetBrains Mono", monospace;
    --font-size-display: 48px;
    --font-size-headline-lg: 32px;
    --font-size-headline-md: 24px;
    --font-size-headline-sm: 20px;
    --font-size-body-lg: 18px;
    --font-size-body-md: 16px;
    --font-size-body-sm: 14px;
    --font-size-label-md: 12px;
    --font-size-code: 14px;

    /* [추가 7월 10일 22:05] Spacing - DESIGN.md 기준 4px 단위 그대로 반영 */
    --space-unit: 4px;
    --space-gutter-sm: 16px;
    --space-gutter-md: 24px;
    --space-margin-mobile: 16px;
    --space-margin-desktop: 40px;
    --container-max: 1280px;

    /* [추가 7월 10일 22:05] Shape - DESIGN.md rounded 토큰 그대로 반영 */
    --radius-sm: 0.125rem;
    --radius-default: 0.25rem;
    --radius-md: 0.375rem;
    --radius-lg: 0.5rem;
    --radius-xl: 0.75rem;
    --radius-full: 9999px;
  }

  /* [수정 7월 10일 22:05] Segoe UI/임의 배경색 대신 DESIGN.md의 Inter 폰트, background 색상 변수로 교체 */
  body {
    margin: 0;
    padding: 0;
    background: var(--color-background);
    color: var(--color-on-background);
    font-family: var(--font-family-base);
  }
  * { box-sizing: border-box; }
`;

export default GlobalStyle;
