import { MarketingArchitecture } from '../components/architecture/marketing-architecture';
import { MarketingCta } from '../components/cta/marketing-cta';
import { MarketingFaq } from '../components/faq/marketing-faq';
import { MarketingFeatures } from '../components/features/marketing-features';
import { MarketingFooter } from '../components/footer/marketing-footer';
import { MarketingHeader } from '../components/header/marketing-header';
import { MarketingHero } from '../components/hero/marketing-hero';
import { MarketingStats } from '../components/stats/marketing-stats';
import classes from './marketing-page.module.css';

export function MarketingPage() {
  return (
    <div className={classes.pageWrapper}>
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      <MarketingHeader />

      <main id="main-content" tabIndex={-1} className={classes.mainContent}>
        <MarketingHero />
        <MarketingStats />
        <MarketingFeatures />
        <MarketingArchitecture />
        <MarketingFaq />
        <MarketingCta />
      </main>

      <MarketingFooter />
    </div>
  );
}
