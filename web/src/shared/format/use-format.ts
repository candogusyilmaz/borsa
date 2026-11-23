import { useMemo } from 'react';
import { useLocale } from '~/shared/i18n/locale-context';
import { createFormat } from './format';

export function useFormat() {
  const { formatLocale } = useLocale();
  return useMemo(() => createFormat(formatLocale), [formatLocale]);
}
