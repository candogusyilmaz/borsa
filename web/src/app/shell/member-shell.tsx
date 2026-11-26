import { Trans, useLingui } from '@lingui/react/macro';
import { Link, useLocation } from '@tanstack/react-router';
import { type ReactNode, useLayoutEffect, useRef } from 'react';
import { PortfolioSelector } from '~/features/portfolios';
import { AddTradeButton } from '~/features/trades/components/add-trade-button/add-trade-button';
import { BrandLogo } from '~/shared/components/brand-logo';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { AccountMenu } from './account-menu';
import { BottomNav } from './bottom-nav';
import styles from './member-shell.module.css';

export function MemberShell({ children }: { children: ReactNode }) {
  const isMobile = useIsMobile();
  const pathname = useLocation({ select: (location) => location.pathname });
  const contentViewport = useRef<HTMLDivElement>(null);
  const previousPathname = useRef(pathname);
  const onPositions = pathname.startsWith('/positions');
  const onDashboard = pathname === '/dashboard' || pathname === '/dashboard/';

  useLayoutEffect(() => {
    if (previousPathname.current === pathname) return;

    previousPathname.current = pathname;
    contentViewport.current?.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className={styles.shell} data-positions={onPositions || undefined} data-dashboard={onDashboard || undefined}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>
      {(!isMobile || (!onPositions && !onDashboard)) && (
        <ShellNavigation isMobile={isMobile} pathname={pathname} onPositions={onPositions} />
      )}
      <div ref={contentViewport} className={styles.contentViewport}>
        <main id="main-content" tabIndex={-1} className={styles.main}>
          <div key={pathname} className="app-page-enter">
            {children}
          </div>
        </main>
      </div>
      {isMobile && <BottomNav />}
    </div>
  );
}

function ShellNavigation({ isMobile, pathname, onPositions }: { isMobile: boolean; pathname: string; onPositions: boolean }) {
  const { t } = useLingui();
  const onPortfolio = pathname.startsWith('/portfolios/');

  if (isMobile) {
    return (
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <span className={styles.mobileTitle}>{onPortfolio ? t`Portfolios` : t`Trades`}</span>
          <div className={styles.actions}>
            <AddTradeButton compact />
            <AccountMenu isMobile />
          </div>
        </div>
      </header>
    );
  }

  return (
    <header className={styles.header}>
      <div className={styles.headerInner}>
        <Link to="/dashboard" className={styles.brand} aria-label={t`Canverse dashboard`}>
          <BrandLogo variant="member" />
        </Link>
        <nav className={styles.links} aria-label={t`Main navigation`}>
          <Link to="/dashboard" className={styles.navLink}>
            <Trans>Dashboard</Trans>
          </Link>
          <PortfolioSelector className={styles.navLink} active={onPortfolio}>
            <Trans>Portfolios</Trans>
          </PortfolioSelector>
          <Link to="/positions" className={styles.navLink}>
            <Trans>Positions</Trans>
          </Link>
          <Link to="/trades" className={styles.navLink}>
            <Trans>Trades</Trans>
          </Link>
        </nav>
        <div className={styles.actions}>
          {!onPositions && <AddTradeButton className={styles.addTrade} />}
          <AccountMenu isMobile={false} />
        </div>
      </div>
    </header>
  );
}
