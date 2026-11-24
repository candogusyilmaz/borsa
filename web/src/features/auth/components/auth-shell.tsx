import { Trans } from '@lingui/react/macro';
import { IconArrowLeft } from '@tabler/icons-react';
import { Link } from '@tanstack/react-router';
import clsx from 'clsx';
import type { ReactNode } from 'react';
import { BrandMark } from '~/shared/components/brand-logo';
import { ThemeToggle } from '~/shared/components/theme-toggle';
import { LanguageSelect } from '~/shared/i18n/language-select';
import styles from '../pages/auth.module.css';

interface AuthShellProps {
  title: ReactNode;
  subtitle: ReactNode;
  footer: ReactNode;
  legal?: ReactNode;
  wide?: boolean;
  children: ReactNode;
}

export function DesktopAuthShell({ title, subtitle, footer, legal, wide, children }: AuthShellProps) {
  return (
    <div className={styles.desktopPage}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>

      <header className={styles.desktopTopBar}>
        <Link to="/" className={styles.homeLink}>
          <IconArrowLeft size={16} stroke={1.7} aria-hidden="true" />
          <Trans>Home</Trans>
        </Link>
        <div className={styles.preferences}>
          <LanguageSelect />
          <ThemeToggle />
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className={styles.desktopMain}>
        <div className={clsx(styles.desktopColumn, 'app-page-enter')} data-wide={wide || undefined}>
          <div className={styles.heading}>
            <BrandMark size={28} className={styles.brandMark} />
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.subtitle}>{subtitle}</p>
          </div>
          {children}
          <div className={styles.switchFooter}>{footer}</div>
          {legal}
        </div>
      </main>
    </div>
  );
}

export function MobileAuthShell({ title, subtitle, footer, legal, children }: AuthShellProps) {
  return (
    <div className={styles.mobilePage}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>

      <header className={styles.mobileTopBar}>
        <Link to="/" className={styles.homeLink}>
          <IconArrowLeft size={16} stroke={1.7} aria-hidden="true" />
          <Trans>Home</Trans>
        </Link>
        <div className={styles.preferences}>
          <LanguageSelect />
          <ThemeToggle />
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className={styles.mobileMain}>
        <div className={clsx(styles.mobileColumn, 'app-page-enter')}>
          <div className={styles.heading}>
            <BrandMark size={28} className={styles.brandMark} />
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.subtitle}>{subtitle}</p>
          </div>
          {children}
          <div className={styles.switchFooter}>{footer}</div>
          {legal}
        </div>
      </main>
    </div>
  );
}
