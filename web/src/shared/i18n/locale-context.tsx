import { i18n } from '@lingui/core';
import { I18nProvider, useLingui } from '@lingui/react';
import { DatesProvider } from '@mantine/dates';
import type { ReactNode } from 'react';
import { getLanguage, setLanguage } from './i18n';

const formatLocales = { en: 'en-US', tr: 'tr-TR' };

function LocalizedDates({ children }: { children: ReactNode }) {
  const { language } = useLocale();
  return <DatesProvider settings={{ locale: language }}>{children}</DatesProvider>;
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  return (
    <I18nProvider i18n={i18n}>
      <LocalizedDates>{children}</LocalizedDates>
    </I18nProvider>
  );
}

export function useLocale() {
  const { i18n } = useLingui();
  const language = getLanguage(i18n.locale);
  return { language, formatLocale: formatLocales[language], setLanguage };
}
