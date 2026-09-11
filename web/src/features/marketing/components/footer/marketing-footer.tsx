import { Container, Group } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import classes from './marketing-footer.module.css';

export function MarketingFooter() {
  return (
    <footer className={classes.footer} role="contentinfo">
      <Container size="lg">
        <div className={classes.topGrid}>
          <div className={classes.brandCol}>
            <Link to="/" className={classes.brand}>
              <BrandLogo variant="full" size="md" />
            </Link>
            <p className={classes.brandDesc}>
              Institutional-grade portfolio intelligence, double-entry cash ledger accounting, and real-time reconciliation.
            </p>
          </div>

          <div className={classes.linksCol}>
            <span className={classes.colTitle}>{siteConfig.name}</span>
            <Link to="/app" className={classes.footerLink}>
              Terminal (/app)
            </Link>
            <Link to="/login" className={classes.footerLink}>
              Sign In
            </Link>
            <a href="#features" className={classes.footerLink}>
              Features
            </a>
            <a href="#stats" className={classes.footerLink}>
              Platform Metrics
            </a>
          </div>

          <div className={classes.linksCol}>
            <span className={classes.colTitle}>Architecture</span>
            <a href="#architecture" className={classes.footerLink}>
              System Stack
            </a>
            <a href="#security" className={classes.footerLink}>
              Zero-Trust Security
            </a>
            <a href="#faq" className={classes.footerLink}>
              FAQ
            </a>
          </div>

          <div className={classes.linksCol}>
            <span className={classes.colTitle}>Engineering</span>
            <span className={classes.techBadge}>Spring Boot 3.4</span>
            <span className={classes.techBadge}>Java 25 LTS</span>
            <span className={classes.techBadge}>React 19 Strict</span>
            <span className={classes.techBadge}>TanStack Router</span>
          </div>
        </div>

        <div className={classes.bottomBar}>
          <div className={classes.statusIndicator}>
            <span className={classes.statusDot} />
            <span>ALL SYSTEMS OPERATIONAL &bull; API v1 ACTIVE</span>
          </div>

          <Group gap="md">
            <span className={classes.copyright}>{`© ${new Date().getFullYear()} ${siteConfig.name} Inc. All rights reserved.`}</span>
            <ThemeToggle />
          </Group>
        </div>
      </Container>
    </footer>
  );
}
