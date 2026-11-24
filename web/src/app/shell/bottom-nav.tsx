import { Trans, useLingui } from '@lingui/react/macro';
import { RemoveScroll } from '@mantine/core';
import { IconArrowsLeftRight, IconBriefcase, IconLayoutGrid, IconStack2 } from '@tabler/icons-react';
import { Link, useLocation } from '@tanstack/react-router';
import { PortfolioSelector } from '~/features/portfolios';
import styles from './bottom-nav.module.css';

export function BottomNav() {
  const { t } = useLingui();
  const onPortfolio = useLocation({ select: (location) => location.pathname.startsWith('/portfolios/') });

  return (
    <nav className={`${styles.nav} ${RemoveScroll.classNames.fullWidth}`} aria-label={t`Main navigation`}>
      <Link to="/dashboard" className={styles.tab}>
        <IconLayoutGrid aria-hidden="true" />
        <Trans>Dashboard</Trans>
      </Link>
      <PortfolioSelector className={styles.tab} active={onPortfolio}>
        <IconBriefcase aria-hidden="true" />
        <Trans comment="Short label for the portfolio switcher in the mobile tab bar">Portfolio</Trans>
      </PortfolioSelector>
      <Link to="/positions" className={styles.tab}>
        <IconStack2 aria-hidden="true" />
        <Trans comment="Short label for the list of holdings in the mobile tab bar">Position</Trans>
      </Link>
      <Link to="/trades" className={styles.tab}>
        <IconArrowsLeftRight aria-hidden="true" />
        <Trans>Trades</Trans>
      </Link>
    </nav>
  );
}
