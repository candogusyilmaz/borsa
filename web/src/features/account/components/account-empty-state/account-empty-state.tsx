import { Button } from '@mantine/core';
import { BankIcon, PlusIcon } from '@phosphor-icons/react';
import classes from './account-empty-state.module.css';

export interface AccountEmptyStateProps {
  onOpenCreate: () => void;
}

export function AccountEmptyState({ onOpenCreate }: AccountEmptyStateProps) {
  return (
    <div className={classes.emptyState}>
      <div className={classes.emptyIcon} aria-hidden="true">
        <BankIcon size={28} weight="light" />
      </div>
      <h3 className={classes.emptyTitle}>No accounts yet</h3>
      <p className={classes.emptyText}>Create your first account to start tracking cash, portfolios, or liabilities.</p>
      <Button
        color="brand"
        size="md"
        className={classes.createBtn}
        leftSection={<PlusIcon size={18} weight="bold" />}
        onClick={onOpenCreate}>
        Create Account
      </Button>
    </div>
  );
}
