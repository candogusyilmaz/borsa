import { createFileRoute, redirect } from '@tanstack/react-router';
import { resolveSession } from '@/api/session';
import { MarketingPage } from '@/features/marketing';
import { siteConfig } from '@/shared/config/site';
import { isStandaloneApp } from '@/shared/utils/is-standalone-app';
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
  beforeLoad: async ({ context }) => {
    if (!isStandaloneApp()) {
      return;
    }

    const session = await resolveSession(context.queryClient);
    throw redirect({ to: session.status === 'authenticated' ? '/app' : '/login', replace: true });
  },
  component: MarketingPage
});
