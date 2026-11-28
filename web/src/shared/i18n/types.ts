export const appLanguages = ['en', 'tr'] as const;
export type AppLanguage = (typeof appLanguages)[number];
export const sourceLanguage: AppLanguage = 'en';

export function isAppLanguage(value: unknown): value is AppLanguage {
  return appLanguages.some((language) => language === value);
}
