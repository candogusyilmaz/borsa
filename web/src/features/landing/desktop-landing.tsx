import { Trans, useLingui } from '@lingui/react/macro';
import { Button } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import clsx from 'clsx';
import { BrandLogo } from '~/shared/components/brand-logo';
import { ThemeToggle } from '~/shared/components/theme-toggle';
import { LanguageSelect } from '~/shared/i18n/language-select';
import styles from './landing.module.css';
import type { LandingViewModel } from './use-landing-view';

export function DesktopLanding({ model }: { model: LandingViewModel }) {
  const { t } = useLingui();

  return (
    <div className={styles.page}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>

      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand} aria-label={t`Canverse home`}>
            <BrandLogo />
          </Link>

          <div className={styles.headerActions}>
            <LanguageSelect />
            <ThemeToggle />
            {model.isAuthenticated ? (
              <Button component={Link} to="/dashboard" radius="md">
                <Trans>Dashboard</Trans>
              </Button>
            ) : (
              <>
                <Link to="/login" className={styles.navLink}>
                  <Trans>Sign in</Trans>
                </Link>
                <Button component={Link} to="/register" radius="md">
                  <Trans>Create account</Trans>
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className={clsx(styles.main, 'app-page-enter')}>
        <section className={styles.hero} aria-labelledby="landing-hero-title">
          <h1 id="landing-hero-title" className={styles.heroTitle}>
            <Trans>Portfolio and trade tracking, kept simple.</Trans>
          </h1>
          <p className={styles.heroSubtitle}>
            <Trans>
              Record buy and sell trades across portfolios and currencies, follow open positions, and review realized profit and loss in one
              place.
            </Trans>
          </p>
          <div className={styles.heroActions}>
            {model.isAuthenticated ? (
              <Button component={Link} to="/dashboard" size="md" radius="md">
                <Trans>Open dashboard</Trans>
              </Button>
            ) : (
              <>
                <Button component={Link} to="/register" size="md" radius="md">
                  <Trans>Create account</Trans>
                </Button>
                <Button component={Link} to="/login" variant="default" size="md" radius="md">
                  <Trans>Sign in</Trans>
                </Button>
              </>
            )}
          </div>
        </section>

        <section className={styles.section} aria-labelledby="landing-capabilities-title">
          <h2 id="landing-capabilities-title" className={styles.sectionLabel}>
            <Trans>What’s included</Trans>
          </h2>
          <ul className={styles.capabilityGrid}>
            {model.capabilities.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.id} className={styles.capabilityItem}>
                  <div className={styles.capabilityHeader}>
                    <Icon size={18} stroke={1.6} aria-hidden />
                    <h3>{t(item.title)}</h3>
                  </div>
                  <p>{t(item.description)}</p>
                </li>
              );
            })}
          </ul>
        </section>

        <section className={styles.section} aria-labelledby="landing-details-title">
          <h2 id="landing-details-title" className={styles.sectionLabel}>
            <Trans>Built for daily use</Trans>
          </h2>
          <dl className={styles.detailGrid}>
            {model.details.map((detail) => (
              <div key={detail.id} className={styles.detailItem}>
                <dt>{t(detail.title)}</dt>
                <dd>{t(detail.description)}</dd>
              </div>
            ))}
          </dl>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerMeta}>
            <span className={styles.footerBrand}>Canverse</span>
            <span className={styles.footerSeparator} aria-hidden="true">
              ·
            </span>
            <span>
              <Trans>Personal portfolio and trade tracker</Trans>
            </span>
          </div>

          <nav className={styles.footerLinks} aria-label={t`Footer links`}>
            {model.isAuthenticated ? (
              <Link to="/dashboard">
                <Trans>Dashboard</Trans>
              </Link>
            ) : (
              <>
                <Link to="/login">
                  <Trans>Sign in</Trans>
                </Link>
                <Link to="/register">
                  <Trans>Create account</Trans>
                </Link>
              </>
            )}
            <Link to="/terms">
              <Trans>Terms</Trans>
            </Link>
            <Link to="/privacy">
              <Trans>Privacy</Trans>
            </Link>
            <a href={model.contactHref}>
              <Trans>Contact</Trans>
            </a>
          </nav>
        </div>
      </footer>
    </div>
  );
}
