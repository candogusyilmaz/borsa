import { Badge } from '@mantine/core';
import { PlusIcon } from '@phosphor-icons/react';
import classes from './account-layout-header.module.css';

export interface AccountLayoutHeaderProps {
  activeCount: number;
  onOpenCreate: () => void;
}

export function AccountLayoutHeader({ activeCount, onOpenCreate }: AccountLayoutHeaderProps) {
  return (
    <div className={classes.headerRow}>
      <div className={classes.headerText}>
        <div className={classes.titleArea}>
          <h1 id="accounts-page-title" className={classes.pageTitle}>
            Financial Accounts
          </h1>
          {activeCount > 0 && (
            <Badge color="brand" variant="light" size="sm" className={classes.desktopBadge}>
              {activeCount} Active
            </Badge>
          )}
        </div>
        <p className={classes.pageSubtitle}>Manage your cash ledgers and portfolios.</p>
      </div>

      <div className={classes.headerActions}>
        <button type="button" className={classes.createBtn} onClick={onOpenCreate} aria-label="Create new financial account">
          <PlusIcon size={20} weight="bold" />
        </button>
      </div>
    </div>
  );
}
