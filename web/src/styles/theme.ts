/**
 * CANVERSE — theme.ts
 * Mantine v9 alpha (9.0.0-alpha.4+)
 */

import { type CSSVariablesResolver, createTheme, type MantineColorsTuple, rem } from '@mantine/core';

// ─────────────────────────────────────────────────────────────────
//  COLOR PALETTES
// ─────────────────────────────────────────────────────────────────

/** Primary brand — indigo/violet spectrum */
const brand: MantineColorsTuple = [
  '#eef2ff', // 0  lightest tint
  '#e0e7ff', // 1
  '#c7d2fe', // 2
  '#a5b4fc', // 3
  '#818cf8', // 4  muted accent, inactive icons
  '#6366f1', // 5  active states, pips, focus rings
  '#4f46e5', // 6  filled buttons ← primaryShade.dark
  '#4338ca', // 7  hover
  '#3730a3', // 8
  '#312e81' // 9
];

/** Violet — crypto assets, logo gradient end */
const violet: MantineColorsTuple = [
  '#faf5ff', // 0
  '#f3e8ff', // 1
  '#e9d5ff', // 2
  '#d8b4fe', // 3
  '#c084fc', // 4
  '#a855f7', // 5  crypto tint, logo gradient end
  '#9333ea', // 6
  '#7e22ce', // 7
  '#6b21a8', // 8
  '#581c87' // 9
];

/**
 * Profit — semantic green for ALL positive P&L, gains, buy actions.
 * NEVER use Mantine's built-in green for financial data — only this.
 */
const profit: MantineColorsTuple = [
  '#f0fdf4', // 0
  '#dcfce7', // 1  badge bg (light mode)
  '#bbf7d0', // 2
  '#86efac', // 3
  '#4ade80', // 4  light variant
  '#22c55e', // 5  PRIMARY display
  '#16a34a', // 6  buy button gradient start
  '#15803d', // 7
  '#166534', // 8
  '#14532d' // 9
];

/**
 * Loss — semantic rose for ALL negative P&L, losses, sell actions.
 * NEVER use Mantine's built-in red for financial data — only this.
 */
const loss: MantineColorsTuple = [
  '#fff1f2', // 0
  '#ffe4e6', // 1  badge bg (light mode)
  '#fecdd3', // 2
  '#fda4af', // 3
  '#fb7185', // 4  light variant
  '#f43f5e', // 5  PRIMARY display
  '#be123c', // 6  sell button gradient start
  '#9f1239', // 7
  '#881337', // 8
  '#4c0519' // 9
];

// ─────────────────────────────────────────────────────────────────
//  THEME
// ─────────────────────────────────────────────────────────────────

export const theme = createTheme({
  // ── Primary ──────────────────────────────────────────────────
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 5 },

  // ── Palettes ─────────────────────────────────────────────────
  colors: { brand, violet, profit, loss },

  // ── Fonts ────────────────────────────────────────────────────
  fontFamily: "'DM Sans', system-ui, -apple-system, sans-serif",
  fontFamilyMonospace: "'JetBrains Mono', ui-monospace, monospace",

  headings: {
    fontFamily: "'DM Sans', system-ui, -apple-system, sans-serif",
    fontWeight: '900',
    sizes: {
      h1: { fontSize: rem(28), lineHeight: '1.2', fontWeight: '900' },
      h2: { fontSize: rem(22), lineHeight: '1.25', fontWeight: '900' },
      h3: { fontSize: rem(18), lineHeight: '1.3', fontWeight: '800' },
      h4: { fontSize: rem(15), lineHeight: '1.4', fontWeight: '800' },
      h5: { fontSize: rem(13), lineHeight: '1.45', fontWeight: '700' },
      h6: { fontSize: rem(11), lineHeight: '1.5', fontWeight: '700' }
    }
  },

  // ── v9: fontWeights — NEW property in Mantine v9 ─────────────
  // Maps to CSS vars: --mantine-font-weight-regular/medium/bold
  // All components now read these vars instead of hardcoded values.
  // v9 changed medium default from 500 → 600; we keep 600 as-is
  // but bump bold to 800 for the heavier financial dashboard feel.
  fontWeights: {
    regular: '400',
    medium: '600', // v9 default — DM Sans 600 reads well in tables
    bold: '800' // headings, button labels, section titles
  },

  // ── Font sizes (Canverse scale — tighter than Mantine defaults) ─
  /* fontSizes: {
    xs: rem(10), // micro caps, badge text, tab labels
    sm: rem(11), // secondary body, muted info
    md: rem(13), // primary body, table cells, nav links
    lg: rem(15), // modal/sheet section headers
    xl: rem(18), // page titles
  }, */

  lineHeights: {
    xs: '1.35',
    sm: '1.4',
    md: '1.5',
    lg: '1.55',
    xl: '1.6'
  },

  // ── Spacing (slightly tighter than Mantine defaults) ──────────
  /* spacing: {
    xs: rem(6),
    sm: rem(10),
    md: rem(16),
    lg: rem(20),
    xl: rem(32),
  }, */

  // ── Border radius (generous for glassmorphism aesthetic) ──────
  radius: {
    xs: rem(4),
    sm: rem(6),
    md: rem(8),
    lg: rem(12),
    xl: rem(16)
  },
  defaultRadius: 'xl',

  // ── Shadows (brand-tinted indigo glow) ───────────────────────
  shadows: {
    xs: '0 1px 3px rgba(0,0,0,0.08)',
    sm: `0 ${rem(4)}  ${rem(12)} rgba(79,70,229,0.14)`,
    md: `0 ${rem(8)}  ${rem(24)} rgba(79,70,229,0.24)`,
    lg: `0 ${rem(16)} ${rem(40)} rgba(79,70,229,0.34)`,
    xl: `0 ${rem(24)} ${rem(56)} rgba(79,70,229,0.44)`
  },

  // ── Breakpoints ───────────────────────────────────────────────
  breakpoints: {
    xs: '30em', // 480px
    sm: '48em', // 768px  ← mobile/desktop split
    md: '64em', // 1024px
    lg: '80em', // 1280px
    xl: '90em' // 1440px
  },

  cursorType: 'pointer',
  focusRing: 'auto',

  // ── Components ────────────────────────────────────────────────
  // NOTE (v9): Component `styles` callbacks no longer receive
  // (theme, props) arguments. Use plain style objects instead.
  // Per-variant conditional styles belong in CSS / canverse-mantine-vars.css.
  components: {
    Button: {
      defaultProps: { radius: 'lg' },
      styles: {
        root: {
          // Base transition — variant-specific gradient applied in CSS
          transition: 'transform 0.2s ease, box-shadow 0.2s ease'
        },
        label: {
          // fontWeights.bold (800) flows in via --mantine-font-weight-bold
          letterSpacing: '0.01em'
        }
      }
    },

    TextInput: { defaultProps: { radius: 'md' } },
    NumberInput: { defaultProps: { radius: 'md' } },
    PasswordInput: { defaultProps: { radius: 'md' } },
    Select: { defaultProps: { radius: 'md' } },
    MultiSelect: { defaultProps: { radius: 'md' } },
    Textarea: { defaultProps: { radius: 'md' } },

    Paper: { defaultProps: { radius: 'xl', withBorder: true } },
    Card: { defaultProps: { radius: 'xl', padding: 'lg', withBorder: true } },

    Modal: {
      defaultProps: {
        radius: 'xl',
        centered: true,
        overlayProps: { blur: 10, backgroundOpacity: 0.58 },
        transitionProps: {
          transition: 'scale',
          duration: 220,
          timingFunction: 'cubic-bezier(0.34, 1.56, 0.64, 1)'
        }
      },
      styles: {
        title: { letterSpacing: '-0.03em' },
        header: { paddingBottom: rem(16) },
        body: { paddingTop: rem(16) }
      }
    },

    // NOTE: v9 renamed TypographyStylesProvider → Typography.
    // Reference it as 'Typography' if you need component overrides.

    Drawer: {
      styles: {
        title: { fontSize: rem(15) }
      }
    },

    ActionIcon: {
      defaultProps: { radius: 'md', variant: 'subtle' },
      styles: { root: { transition: 'all 0.15s ease' } }
    },

    Avatar: {
      defaultProps: { radius: 'md' }
    },

    NavLink: {
      defaultProps: { radius: 'lg' },
      styles: {
        root: { transition: 'all 0.25s ease' },
        label: { fontSize: rem(13) }
      }
    },

    Tabs: {
      styles: {
        tab: { fontSize: rem(13), transition: 'all 0.2s ease' }
      }
    },

    Table: {
      defaultProps: {
        highlightOnHover: true,
        withColumnBorders: false,
        withRowBorders: true
      },
      styles: {
        th: {
          fontSize: rem(10),
          textTransform: 'uppercase',
          letterSpacing: '0.18em',
          paddingTop: rem(10),
          paddingBottom: rem(10),
          opacity: 0.35
        },
        td: {
          fontSize: rem(13),
          paddingTop: rem(14),
          paddingBottom: rem(14)
        }
      }
    },

    Tooltip: {
      defaultProps: { radius: 'md', withArrow: true, arrowSize: 6 },
      styles: {
        tooltip: {
          fontFamily: "'DM Sans', sans-serif",
          fontSize: rem(12)
        }
      }
    },

    Notification: {
      defaultProps: { radius: 'lg' },
      styles: {
        title: { fontSize: rem(13) },
        description: { fontSize: rem(12) }
      }
    },

    SegmentedControl: {
      defaultProps: { radius: 'lg' },
      styles: { label: { fontSize: rem(12) } }
    },

    Loader: { defaultProps: { type: 'dots', color: 'brand' } },
    Progress: { defaultProps: { radius: 'xl' } },
    Skeleton: { defaultProps: { radius: 'md', animate: true } }
  }
});

// ─────────────────────────────────────────────────────────────────
//  CSS VARIABLES RESOLVER
//  Injects --cv-* tokens and Mantine semantic overrides into the DOM.
//  Called by MantineProvider at mount — available from frame 1.
// ─────────────────────────────────────────────────────────────────

export const cssVariablesResolver: CSSVariablesResolver = (_theme) => ({
  // :root — static tokens, identical in both color schemes
  variables: {
    // Brand raw values
    '--cv-brand-400': '#818cf8',
    '--cv-brand-500': '#6366f1',
    '--cv-brand-600': '#4f46e5',
    '--cv-violet-500': '#a855f7',

    // Profit (green)
    '--cv-profit': '#22c55e',
    '--cv-profit-light': '#4ade80',
    '--cv-profit-dark': '#16a34a',
    '--cv-profit-dim': 'rgba(34,197,94,0.14)',
    '--cv-profit-glow': 'rgba(34,197,94,0.28)',
    '--cv-profit-muted': 'rgba(34,197,94,0.12)',

    // Loss (rose)
    '--cv-loss': '#f43f5e',
    '--cv-loss-light': '#fb7185',
    '--cv-loss-dark': '#be123c',
    '--cv-loss-dim': 'rgba(244,63,94,0.13)',
    '--cv-loss-glow': 'rgba(244,63,94,0.28)',
    '--cv-loss-muted': 'rgba(244,63,94,0.10)',

    // Gradients
    '--cv-gradient-brand': 'linear-gradient(135deg, #4f46e5, #6366f1)',
    '--cv-gradient-logo': 'linear-gradient(135deg, #4f46e5, #a855f7)',
    '--cv-gradient-buy': 'linear-gradient(135deg, #16a34a, #22c55e)',
    '--cv-gradient-sell': 'linear-gradient(135deg, #be123c, #f43f5e)',

    // Shadows
    '--cv-shadow-brand': '0 8px 24px rgba(79,70,229,0.40)',
    '--cv-shadow-brand-md': '0 12px 32px rgba(79,70,229,0.48)',
    '--cv-shadow-brand-lg': '0 16px 40px rgba(79,70,229,0.55)',
    '--cv-shadow-brand-xl': '0 24px 56px rgba(79,70,229,0.65)',
    '--cv-shadow-fab': '0 8px 24px rgba(79,70,229,0.55)',
    '--cv-shadow-fab-drag': '0 20px 48px rgba(79,70,229,0.75), 0 0 0 3px rgba(99,102,241,0.30)',
    '--cv-shadow-profit': '0 0 16px rgba(34,197,94,0.30)',
    '--cv-shadow-loss': '0 0 16px rgba(244,63,94,0.30)',
    '--cv-shadow-pip': '0 0 10px rgba(99,102,241,0.90)',

    // Blur values (used in backdrop-filter across components)
    '--cv-modal-blur': 'blur(40px)',
    '--cv-card-blur': 'blur(24px)',
    '--cv-sidebar-blur': 'blur(16px)',
    '--cv-sheet-blur': 'blur(32px)',
    '--cv-topbar-blur': 'blur(20px)',

    // Easing curves
    '--cv-ease-spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
    '--cv-ease-smooth': 'cubic-bezier(0.32, 0.72, 0,    1)',
    '--cv-ease-throw': 'cubic-bezier(0.25, 0.46, 0.45, 0.94)',

    // Z-index layers (above Mantine's own z-index scale)
    '--cv-z-topbar': '5',
    '--cv-z-fab': '24',
    '--cv-z-bottom-nav': '25',
    '--cv-z-backdrop': '28',
    '--cv-z-sheet': '29'
  },

  // [data-mantine-color-scheme="light"]
  light: {
    // Mantine semantic tokens
    '--mantine-color-body': '#f8fafc',
    '--mantine-color-text': '#0f172a',
    '--mantine-color-bright': '#0f172a',
    '--mantine-color-dimmed': '#64748b',
    '--mantine-color-placeholder': '#94a3b8',
    '--mantine-color-anchor': '#6366f1',
    '--mantine-color-default': 'rgba(255,255,255,0.90)',
    '--mantine-color-default-hover': '#f1f5f9',
    '--mantine-color-default-color': '#0f172a',
    '--mantine-color-default-border': '#e2e8f0',
    '--mantine-color-error': '#f43f5e',
    '--mantine-color-disabled': '#f1f5f9',
    '--mantine-color-disabled-color': '#94a3b8',
    '--mantine-color-disabled-border': '#e2e8f0',

    // Primary (brand indigo) — light mode
    '--mantine-primary-color-contrast': '#ffffff',
    '--mantine-primary-color-filled': '#4f46e5',
    '--mantine-primary-color-filled-hover': '#4338ca',
    // v9: light variant uses opaque values — no transparency
    '--mantine-primary-color-light': '#eef2ff',
    '--mantine-primary-color-light-hover': '#e0e7ff',
    '--mantine-primary-color-light-color': '#4f46e5',

    // Canverse surface tokens — light
    '--cv-page-gradient': 'radial-gradient(ellipse at top left, #eff6ff, #f8fafc, #f1f5f9)',
    '--cv-sidebar-bg': 'rgba(255,255,255,0.60)',
    '--cv-card-bg': 'rgba(255,255,255,0.55)',
    '--cv-topbar-bg': 'rgba(248,250,252,0.82)',
    '--cv-bottom-nav-bg': 'rgba(255,255,255,0.96)',
    '--cv-sheet-bg': 'rgba(255,255,255,0.97)',
    '--cv-modal-bg': 'rgba(255,255,255,0.96)',
    '--cv-input-bg': '#f8fafc',
    '--cv-border': '#e2e8f0',
    '--cv-border-light': '#f1f5f9',
    '--cv-row-hover': '#f1f5f9',
    '--cv-card-shadow': '0 1px 3px rgba(0,0,0,0.06)',
    '--cv-modal-shadow': '0 24px 48px rgba(0,0,0,0.12)',
    '--cv-sheet-shadow': '0 -8px 30px rgba(0,0,0,0.08)',
    '--cv-text-main': '#0f172a',
    '--cv-text-muted': '#64748b',
    '--cv-text-xmuted': 'rgba(15,23,42,0.35)',
    '--cv-icon-inactive-bg': 'rgba(79,70,229,0.08)',
    '--cv-icon-inactive-color': '#4f46e5'
  },

  // [data-mantine-color-scheme="dark"]
  dark: {
    // Mantine semantic tokens
    '--mantine-color-body': '#020617',
    '--mantine-color-text': '#f1f5f9',
    '--mantine-color-bright': '#ffffff',
    '--mantine-color-dimmed': '#94a3b8',
    '--mantine-color-placeholder': '#64748b',
    '--mantine-color-anchor': '#818cf8',
    '--mantine-color-default': 'rgba(255,255,255,0.05)',
    '--mantine-color-default-hover': 'rgba(255,255,255,0.09)',
    '--mantine-color-default-color': '#f1f5f9',
    '--mantine-color-default-border': 'rgba(255,255,255,0.09)',
    '--mantine-color-error': '#f43f5e',
    '--mantine-color-disabled': 'rgba(255,255,255,0.04)',
    '--mantine-color-disabled-color': '#64748b',
    '--mantine-color-disabled-border': 'rgba(255,255,255,0.06)',

    // Primary (brand indigo) — dark mode
    '--mantine-primary-color-contrast': '#ffffff',
    '--mantine-primary-color-filled': '#6366f1',
    '--mantine-primary-color-filled-hover': '#4f46e5',
    // v9: light variant uses opaque values — no transparency
    '--mantine-primary-color-light': '#1e1b4b',
    '--mantine-primary-color-light-hover': '#312e81',
    '--mantine-primary-color-light-color': '#818cf8',

    // Canverse surface tokens — dark
    '--cv-page-gradient': 'radial-gradient(ellipse at top left, #0f172a, #020617, #000000)',
    '--cv-sidebar-bg': 'rgba(0,0,0,0.35)',
    '--cv-card-bg': 'rgba(255,255,255,0.05)',
    '--cv-topbar-bg': 'rgba(2,6,23,0.82)',
    '--cv-bottom-nav-bg': 'rgba(4,6,18,0.96)',
    '--cv-sheet-bg': 'rgba(6,8,20,0.97)',
    '--cv-modal-bg': 'rgba(8,10,22,0.96)',
    '--cv-input-bg': 'rgba(255,255,255,0.05)',
    '--cv-border': 'rgba(255,255,255,0.09)',
    '--cv-border-light': 'rgba(255,255,255,0.04)',
    '--cv-row-hover': 'rgba(255,255,255,0.025)',
    '--cv-card-shadow': 'none',
    '--cv-modal-shadow': '0 32px 80px rgba(0,0,0,0.80), 0 0 0 1px rgba(255,255,255,0.04)',
    '--cv-sheet-shadow': '0 -20px 60px rgba(0,0,0,0.50)',
    '--cv-text-main': '#f1f5f9',
    '--cv-text-muted': '#94a3b8',
    '--cv-text-xmuted': 'rgba(241,245,249,0.30)',
    '--cv-icon-inactive-bg': 'rgba(255,255,255,0.08)',
    '--cv-icon-inactive-color': 'rgba(241,245,249,0.60)'
  }
});
