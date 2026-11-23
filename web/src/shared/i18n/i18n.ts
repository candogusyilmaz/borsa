import { i18n } from '@lingui/core';
import { resolveInitialPreferences } from './preferences-adapter';
import { type AppLanguage, resolvePreferences, sourceLanguage } from './types';

const catalogs = {
  en: () => import('../../locales/en/messages.po'),
  tr: () => import('../../locales/tr/messages.po')
};

const dateLocales = {
  en: () => import('dayjs/locale/en'),
  tr: () => import('dayjs/locale/tr')
};

let preferences = resolvePreferences(sourceLanguage);
let activation = 0;
const listeners = new Set<() => void>();

export function getLocalePreferences() {
  return preferences;
}

export function subscribeLocale(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function activateLocale(language: AppLanguage, countryCode?: string | null) {
  const request = ++activation;
  let catalog: Awaited<ReturnType<(typeof catalogs)[AppLanguage]>>;
  try {
    [catalog] = await Promise.all([catalogs[language](), dateLocales[language]()]);
  } catch (error) {
    if (request !== activation) return false;
    throw error;
  }
  if (request !== activation) return false;

  const next = resolvePreferences(language, countryCode);
  const changed = next.language !== preferences.language || next.countryCode !== preferences.countryCode;
  if (changed) preferences = next;
  document.documentElement.lang = language;
  if (i18n.locale !== language) i18n.loadAndActivate({ locale: language, messages: catalog.messages });
  if (changed) for (const listener of listeners) listener();
  return true;
}

export async function initLocale() {
  const initial = resolveInitialPreferences();
  try {
    await activateLocale(initial.language, initial.countryCode);
  } catch (error) {
    if (initial.language === sourceLanguage) throw error;
    console.error('Could not load the preferred locale', error);
    await activateLocale(sourceLanguage, initial.countryCode);
  }
}
