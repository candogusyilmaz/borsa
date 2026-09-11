import { createFileRoute } from '@tanstack/react-router';
import { MarketingPage } from '@/features/marketing';
import { siteConfig } from '@/shared/config/site';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/')({
  head: () => {
    const seo = createSeoMeta({
      title: `${siteConfig.name} - ${siteConfig.tagline}`,
      description: siteConfig.description,
      path: '/'
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: MarketingPage
});
