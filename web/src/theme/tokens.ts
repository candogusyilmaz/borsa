export const THEME_PALETTE = {
  light: {
    canvas: '#F3F2EE',
    surface: '#FAF9F6',
    raised: '#EAE8E1',
    border: '#E2DFD7',
    strongBorder: '#CFCBC0',
    text: '#1C1B18',
    dimmed: '#6E6B64',
    placeholder: '#9A968C',
    primary: '#242320',
    profit: '#15803D',
    loss: '#BE123C'
  },
  dark: {
    canvas: '#0D0D0F',
    surface: '#131316',
    raised: '#1B1B20',
    border: '#222228',
    strongBorder: '#34343D',
    text: '#F4F4F5',
    dimmed: '#8E8E98',
    placeholder: '#63636E',
    profit: '#34D399',
    loss: '#FB7185'
  }
} as const;

export const MOBILE_BREAKPOINT = '48em';

export const APP_LAYERS = {
  languageSelector: 20,
  desktopHeader: 90,
  mobileStatusBar: 95,
  bottomNav: 100,
  // Portalled dialog pickers sit above Mantine dialogs (200) and popovers (300), below notifications (400).
  dialogPicker: 350,
  skipLink: 1000
} as const;
