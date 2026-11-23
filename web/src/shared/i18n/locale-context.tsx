import { i18n } from '@lingui/core';
import { I18nProvider } from '@lingui/react';
import { DatesProvider } from '@mantine/dates';
import { createContext, type ReactNode, useContext, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { activateLocale, getLocalePreferences, subscribeLocale } from './i18n';
import {
  type LocaleProfile,
  resolveInitialPreferences,
  saveDeviceLanguage,
  saveUserLocalePreference,
  syncLocaleFromUser
} from './preferences-adapter';
import type { AppLanguage } from './types';

type LocaleContextValue = {
  language: AppLanguage;
  formatLocale: string;
  setLanguage: (language: AppLanguage) => Promise<void>;
  isChangingLocale: boolean;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({ children, user }: { children: ReactNode; user: LocaleProfile | null }) {
  const preferences = useSyncExternalStore(subscribeLocale, getLocalePreferences);
  const [isChangingLocale, setIsChangingLocale] = useState(false);
  const manualLanguage = useRef<AppLanguage | undefined>(undefined);
  const request = useRef(0);

  useEffect(() => {
    if (isChangingLocale) return;
    const profile = user
      ? syncLocaleFromUser(user, manualLanguage.current)
      : getLocalePreferences().countryCode
        ? resolveInitialPreferences(manualLanguage.current)
        : undefined;
    if (!profile) return;

    void activateLocale(profile.language, profile.countryCode).catch((error) => console.error('Could not apply the profile locale', error));
  }, [user, isChangingLocale]);

  async function setLanguage(language: AppLanguage) {
    const operation = ++request.current;
    setIsChangingLocale(true);
    try {
      if (!(await activateLocale(language, getLocalePreferences().countryCode))) return;
      manualLanguage.current = language;
      saveDeviceLanguage(language);
      if (user)
        void saveUserLocalePreference(getLocalePreferences()).catch((error) => console.error('Could not save the profile locale', error));
    } finally {
      if (operation === request.current) setIsChangingLocale(false);
    }
  }

  const { language, formatLocale } = preferences;
  return (
    <LocaleContext value={{ language, formatLocale, setLanguage, isChangingLocale }}>
      <I18nProvider i18n={i18n}>
        <DatesProvider settings={{ locale: language }}>{children}</DatesProvider>
      </I18nProvider>
    </LocaleContext>
  );
}

export function useLocale() {
  const context = useContext(LocaleContext);
  if (!context) throw new Error('useLocale must be used within a LocaleProvider');
  return context;
}
