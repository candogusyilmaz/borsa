export type AppLanguage = 'en' | 'tr';
export type FormatLocale = `${AppLanguage}-${string}`;

export interface UserLocalePreferences {
  language: AppLanguage;
  countryCode?: string | null; // TODO(backend): supplied by the user profile or onboarding
  formatLocale: string;
}

export const sourceLanguage: AppLanguage = 'en';
export const appLanguages = ['en', 'tr'] as const satisfies readonly AppLanguage[];

const defaultCountries: Record<AppLanguage, string> = { en: 'US', tr: 'TR' };

export function isAppLanguage(value: unknown): value is AppLanguage {
  return value === 'en' || value === 'tr';
}

const countryFormats: Readonly<Record<string, FormatLocale | undefined>> = { TR: 'tr-TR', US: 'en-US', GB: 'en-GB' };

export function resolvePreferences(language: AppLanguage, countryCode?: string | null): UserLocalePreferences {
  const country = countryCode?.trim().toUpperCase();
  const region = country && /^[A-Z]{2}$/.test(country) ? country : null;
  const formatLocale: FormatLocale = (region && countryFormats[region]) || `${language}-${region ?? defaultCountries[language]}`;
  return { language, countryCode: region, formatLocale };
}
