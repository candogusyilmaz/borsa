import { defineConfig } from '@lingui/cli';
import { appLanguages, sourceLanguage } from './src/shared/i18n/types';

export default defineConfig({
  sourceLocale: sourceLanguage,
  locales: [...appLanguages],
  catalogs: [{ path: '<rootDir>/src/locales/{locale}/messages', include: ['src'] }],
  macro: { jsxPlaceholderDefaults: { a: 'link', strong: 'bold' } }
});
