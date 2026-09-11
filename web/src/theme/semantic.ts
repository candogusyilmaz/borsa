export const semantic = {
  light: {
    bg: '#F6F7F9',

    surface: '#FFFFFF',
    surfaceSecondary: '#F0F2F5',
    surfaceElevated: '#FFFFFF',
    surfaceInteractive: '#FAFBFC',
    surfaceHover: '#F3F4F7',
    surfaceSelected: '#EEEDFF',

    textPrimary: '#171B22',
    textSecondary: '#525C6A',
    textTertiary: '#697586',
    textDisabled: '#9AA4B2',

    borderSubtle: '#E8EAF0',
    border: '#DCE0E7',
    borderStrong: '#C9CFD8',

    accent: '#5855E7',
    accentFill: '#5855E7',
    accentHover: '#4845C8',
    accentSubtle: '#EEEDFF',

    success: '#16865A',
    danger: '#C13D4D',
    warning: '#936000',
    info: '#2F6FB0',

    focusRing: 'rgba(88, 85, 231, 0.22)',
    overlay: 'rgba(7, 11, 18, 0.48)',

    shadowRaised: '0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 14px rgba(15, 23, 42, 0.04)',
    shadowFloating: '0 12px 36px rgba(15, 23, 42, 0.12), 0 2px 8px rgba(15, 23, 42, 0.06)'
  },

  dark: {
    bg: '#0C111B',

    surface: '#111827',
    surfaceSecondary: '#151E2D',
    surfaceElevated: '#192334',
    surfaceInteractive: '#172131',
    surfaceHover: '#1E2A3D',
    surfaceSelected: '#23254F',

    textPrimary: '#F3F5F8',
    textSecondary: '#B8C0CC',
    textTertiary: '#8F9AAB',
    textDisabled: '#667386',

    borderSubtle: '#202C3F',
    border: '#2A374C',
    borderStrong: '#3A4961',

    accent: '#A29EFF',
    accentFill: '#5855E7',
    accentHover: '#6A67EF',
    accentSubtle: '#23254F',

    success: '#58D7A0',
    danger: '#FF818D',
    warning: '#F2C468',
    info: '#78BFFF',

    focusRing: 'rgba(162, 158, 255, 0.30)',
    overlay: 'rgba(2, 6, 12, 0.68)',

    shadowRaised: '0 1px 2px rgba(0, 0, 0, 0.18)',
    shadowFloating: '0 18px 48px rgba(0, 0, 0, 0.36), 0 4px 12px rgba(0, 0, 0, 0.22)'
  }
} as const;
