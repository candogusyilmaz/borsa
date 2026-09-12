import { Badge } from '@mantine/core';
import { CardsIcon, CaretRightIcon } from '@phosphor-icons/react';
import type { FinancialAccount } from '../../types';
import classes from './accounts-list-section.module.css';

interface AccountsListSectionProps {
  accounts: FinancialAccount[];
  onOpenAccountPicker: () => void;
}

export function AccountsListSection({ accounts, onOpenAccountPicker }: AccountsListSectionProps) {
  const totalCount = accounts.length;

  return (
    <div className={classes.section}>
      <button type="button" className={classes.actionBanner} onClick={onOpenAccountPicker} aria-label={`View all ${totalCount} accounts`}>
        <div className={classes.bannerLeft}>
          <div className={classes.iconSquircle} aria-hidden="true">
            <CardsIcon size={20} weight="duotone" />
          </div>
          <span className={classes.bannerTitle}>View all accounts</span>
          <Badge color="brand" variant="light" size="sm">
            {totalCount}
          </Badge>
        </div>

        <div className={classes.bannerRight}>
          <span className={classes.browseText}>Browse</span>
          <CaretRightIcon size={18} weight="bold" className={classes.chevronIcon} aria-hidden="true" />
        </div>
      </button>
    </div>
  );
}
