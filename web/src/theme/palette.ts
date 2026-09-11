import { DEFAULT_THEME, type MantineColorsTuple } from '@mantine/core';

export const brand: MantineColorsTuple = [
  '#F2F1FF',
  '#E3E1FF',
  '#C7C3FF',
  '#AAA5FF',
  '#8A85FF',
  '#6C68F5',
  '#5855E7',
  '#4845C8',
  '#3836A4',
  '#29287D'
];

export const neutral: MantineColorsTuple = [
  '#FAFBFC',
  '#F6F7F9',
  '#F0F2F5',
  '#E4E7EC',
  '#D2D7DF',
  '#AEB6C2',
  '#7B8696',
  '#525C6A',
  '#303741',
  '#171B22'
];

export const semanticColors = {
  success: DEFAULT_THEME.colors.teal,
  danger: DEFAULT_THEME.colors.red,
  warning: DEFAULT_THEME.colors.yellow,
  info: DEFAULT_THEME.colors.blue
} as const;
