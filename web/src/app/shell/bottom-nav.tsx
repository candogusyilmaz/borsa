import { RemoveScroll } from '@mantine/core';
import { IconArrowsLeftRight, IconFolderOpen, IconHome, IconListDetails } from '@tabler/icons-react';
import { Link, useLocation } from '@tanstack/react-router';
import { PortfolioSelector } from '~/features/portfolios';
import styles from './bottom-nav.module.css';

export function BottomNav() {
  const onPortfolio = useLocation({ select: (location) => location.pathname.startsWith('/portfolios/') });

  return (
    <nav className={`${styles.nav} ${RemoveScroll.classNames.fullWidth}`} aria-label="Main navigation">
      <Link to="/dashboard" className={styles.tab}>
        <IconHome aria-hidden="true" />
        Dashboard
      </Link>
      <PortfolioSelector className={styles.tab} active={onPortfolio}>
        <IconFolderOpen aria-hidden="true" />
        Portfolios
      </PortfolioSelector>
      <Link to="/positions" className={styles.tab}>
        <IconListDetails aria-hidden="true" />
        Positions
      </Link>
      <Link to="/trades" className={styles.tab}>
        <IconArrowsLeftRight aria-hidden="true" />
        Trades
      </Link>
    </nav>
  );
}
