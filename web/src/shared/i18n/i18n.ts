import { i18n } from '@lingui/core';
import dayjs from 'dayjs';
import 'dayjs/locale/tr';
import { messages as en } from '../../locales/en/messages.po';
import { messages as tr } from '../../locales/tr/messages.po';
import { type AppLanguage, isAppLanguage, sourceLanguage } from './types';

const storageKey = 'canverse:locale';

function readDeviceLanguage(): AppLanguage | undefined {
  try {
    const stored = localStorage.getItem(storageKey);
    if (isAppLanguage(stored)) return stored;
  } catch {
    // Storage can be disabled without preventing language changes in this tab.
  }
}

function browserLanguage(): AppLanguage {
  const preferredLanguage = navigator.languages?.[0] || navigator.language;
  return preferredLanguage?.toLowerCase().split('-')[0] === 'tr' ? 'tr' : sourceLanguage;
}

function activateLanguage(language: AppLanguage) {
  document.documentElement.lang = language;
  dayjs.locale(language);
  if (i18n.locale !== language) i18n.activate(language);
}

export function getLanguage(locale = i18n.locale): AppLanguage {
  return isAppLanguage(locale) ? locale : sourceLanguage;
}

export function initLocale() {
  i18n.load({ en, tr });
  activateLanguage(readDeviceLanguage() ?? browserLanguage());
}

export function setLanguage(language: AppLanguage) {
  activateLanguage(language);
  try {
    localStorage.setItem(storageKey, language);
  } catch {
    // The active selection remains available in memory.
  }
}
