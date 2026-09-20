export interface SiteAssets {
  logo: string;
  favicon: string;
  appleTouchIcon: string;
  ogImage?: string;
  manifest: string;
}

export interface SiteConfig {
  name: string;
  shortName: string;
  tagline: string;
  description: string;
  url: string;
  twitterHandle: string;
  assets: SiteAssets;
  formatTitle: (pageTitle?: string) => string;
  getCanonicalUrl: (path?: string) => string;
}

const env = import.meta.env;

const rawName = (env.VITE_APP_NAME as string | undefined)?.trim();
const name = rawName || 'Platform';

const rawShortName = (env.VITE_APP_SHORT_NAME as string | undefined)?.trim();
const shortName = rawShortName || name;

const rawTagline = (env.VITE_APP_TAGLINE as string | undefined)?.trim();
const tagline = rawTagline || 'Modern Web Platform';

const rawDescription = (env.VITE_APP_DESCRIPTION as string | undefined)?.trim();
const description = rawDescription || 'A scalable, modern web platform.';

const rawUrl = (env.VITE_APP_URL as string | undefined)?.trim() || 'http://localhost:5173';
const url = rawUrl.replace(/\/+$/, '');

const rawTwitter = (env.VITE_APP_TWITTER_HANDLE as string | undefined)?.trim();
const twitterHandle = rawTwitter || '';

const rawOgImage = (env.VITE_APP_OG_IMAGE as string | undefined)?.trim();
const ogImage =
  rawOgImage !== undefined ? (rawOgImage.toLowerCase() === 'none' ? undefined : rawOgImage || undefined) : '/assets/og-image.png';

export const siteConfig: SiteConfig = {
  name,
  shortName,
  tagline,
  description,
  url,
  twitterHandle,
  assets: {
    logo: '/assets/logo.png',
    favicon: '/assets/favicon.png',
    appleTouchIcon: '/assets/apple-touch-icon.png',
    ogImage,
    manifest: '/manifest.webmanifest'
  },
  formatTitle(pageTitle?: string) {
    const trimmed = pageTitle?.trim();
    if (!trimmed || trimmed === name || trimmed === `${name} - ${tagline}`) {
      return `${name} - ${tagline}`;
    }
    if (trimmed.endsWith(`| ${name}`)) {
      return trimmed;
    }
    return `${trimmed} | ${name}`;
  },
  getCanonicalUrl(path = '/') {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    const normalized = path.replace(/^\/+/, '/');
    const cleanPath = normalized.startsWith('/') ? normalized : `/${normalized}`;
    if (cleanPath === '/') {
      return `${url}/`;
    }
    return `${url}${cleanPath.replace(/\/+$/, '')}`;
  }
};
