import type { components } from '~/api/schema';
import { type AppLanguage, isAppLanguage, resolvePreferences, sourceLanguage, type UserLocalePreferences } from './types';

// TODO(backend): replace this boundary type with the generated UserView when /api/account/me is exposed to the web client.
export type LocaleProfile = Pick<components['schemas']['TokenCreateResponse'], 'email'> & {
  language?: unknown;
  countryCode?: unknown;
};

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

export function resolveInitialPreferences(activeLanguage?: AppLanguage): UserLocalePreferences {
  return resolvePreferences(activeLanguage ?? readDeviceLanguage() ?? browserLanguage());
}

export function saveDeviceLanguage(language: AppLanguage) {
  try {
    localStorage.setItem(storageKey, language);
  } catch {
    // The active selection remains available in memory.
  }
}

export function syncLocaleFromUser(user: LocaleProfile, activeLanguage?: AppLanguage): UserLocalePreferences | undefined {
  // TODO(backend): supply the authenticated UserView to this adapter once the profile fields are available.
  const profileLanguage = isAppLanguage(user.language) ? user.language : undefined;
  const countryCode = typeof user.countryCode === 'string' ? user.countryCode : null;
  if (profileLanguage == null && countryCode == null) return;

  return resolvePreferences(activeLanguage ?? readDeviceLanguage() ?? profileLanguage ?? browserLanguage(), countryCode);
}

export async function saveUserLocalePreference(prefs: UserLocalePreferences): Promise<void> {
  // TODO(backend): PATCH /api/account/preferences through the authenticated OpenAPI client.
  // Send language and any explicitly edited country preference, then reconcile the returned UserView.
  void prefs;
}
