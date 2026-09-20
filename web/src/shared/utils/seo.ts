import { siteConfig } from '@/shared/config/site';

export interface SeoMetaOptions {
  title?: string;
  description?: string;
  path?: string;
  image?: string;
  imageWidth?: number | string;
  imageHeight?: number | string;
  imageType?: string;
  type?: 'website' | 'article' | 'profile';
  noIndex?: boolean;
  includeJsonLd?: boolean;
  canonical?: boolean;
}

export interface SeoMetaResult {
  meta: Array<Record<string, string>>;
  links: Array<Record<string, string>>;
  scripts: Array<{ type: string; children: string }>;
}

export function createJsonLdGraph() {
  const logoUrl = siteConfig.assets.logo ? siteConfig.getCanonicalUrl(siteConfig.assets.logo) : undefined;

  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'WebSite',
        '@id': `${siteConfig.url}/#website`,
        url: siteConfig.url,
        name: siteConfig.name,
        description: siteConfig.description
      },
      {
        '@type': 'Organization',
        '@id': `${siteConfig.url}/#organization`,
        name: siteConfig.name,
        url: siteConfig.url,
        ...(logoUrl ? { logo: logoUrl } : {})
      },
      {
        '@type': 'SoftwareApplication',
        '@id': `${siteConfig.url}/#software`,
        name: siteConfig.name,
        applicationCategory: 'FinanceApplication',
        operatingSystem: 'All',
        description: siteConfig.description,
        url: siteConfig.url
      }
    ]
  };
}

function inferImageType(imageUrl: string) {
  const cleanUrl = (imageUrl.split(/[?#]/)[0] ?? imageUrl).toLowerCase();
  if (cleanUrl.endsWith('.jpg') || cleanUrl.endsWith('.jpeg')) return 'image/jpeg';
  if (cleanUrl.endsWith('.webp')) return 'image/webp';
  if (cleanUrl.endsWith('.avif')) return 'image/avif';
  if (cleanUrl.endsWith('.svg')) return 'image/svg+xml';
  if (cleanUrl.endsWith('.gif')) return 'image/gif';
  return 'image/png';
}

function resolveImageUrl(image?: string) {
  const candidate = image !== undefined ? image.trim() : siteConfig.assets.ogImage?.trim() || '';
  if (!candidate || candidate.toLowerCase() === 'none') {
    return undefined;
  }
  if (candidate.startsWith('http://') || candidate.startsWith('https://')) {
    return candidate;
  }
  return siteConfig.getCanonicalUrl(candidate);
}

export function createSeoMeta(options: SeoMetaOptions = {}) {
  const {
    title,
    description = siteConfig.description,
    path = '/',
    image,
    imageWidth,
    imageHeight,
    imageType,
    type = 'website',
    noIndex = false,
    includeJsonLd = false,
    canonical = !noIndex
  } = options;

  const formattedTitle = siteConfig.formatTitle(title);
  const canonicalUrl = siteConfig.getCanonicalUrl(path);
  const imageUrl = resolveImageUrl(image);

  const meta: Array<Record<string, string>> = [
    { title: formattedTitle },
    { name: 'description', content: description },
    {
      name: 'robots',
      content: noIndex ? 'noindex, nofollow, noarchive' : 'index, follow'
    },
    // OpenGraph
    { property: 'og:title', content: formattedTitle },
    { property: 'og:description', content: description },
    { property: 'og:url', content: canonicalUrl },
    { property: 'og:type', content: type },
    { property: 'og:site_name', content: siteConfig.name },
    { property: 'og:locale', content: 'en_US' }
  ];

  if (imageUrl) {
    meta.push(
      { property: 'og:image', content: imageUrl },
      { property: 'og:image:width', content: String(imageWidth ?? 1200) },
      { property: 'og:image:height', content: String(imageHeight ?? 630) },
      { property: 'og:image:type', content: imageType ?? inferImageType(imageUrl) },
      // Twitter Card
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: formattedTitle },
      { name: 'twitter:description', content: description },
      { name: 'twitter:image', content: imageUrl }
    );
  } else {
    meta.push(
      // Twitter Card without image
      { name: 'twitter:card', content: 'summary' },
      { name: 'twitter:title', content: formattedTitle },
      { name: 'twitter:description', content: description }
    );
  }

  if (siteConfig.twitterHandle) {
    meta.push({ name: 'twitter:site', content: siteConfig.twitterHandle }, { name: 'twitter:creator', content: siteConfig.twitterHandle });
  }

  const links: Array<Record<string, string>> = canonical ? [{ rel: 'canonical', href: canonicalUrl }] : [];

  const scripts: Array<{ type: string; children: string }> = [];

  if (includeJsonLd) {
    scripts.push({
      type: 'application/ld+json',
      children: JSON.stringify(createJsonLdGraph()).replace(/</g, '\\u003c')
    });
  }

  return {
    meta,
    links,
    scripts
  };
}
