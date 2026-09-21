import { Button } from '@mantine/core';
import { BankIcon, PlusIcon } from '@phosphor-icons/react';
import { useNavigate } from '@tanstack/react-router';
import { CreateAccountOverlay } from '../create-account/create-account';
import classes from './account-empty-state.module.css';

export interface AccountEmptyStateProps {
  onOpenCreate?: () => void;
}

export function AccountEmptyState({ onOpenCreate }: AccountEmptyStateProps = {}) {
  const navigate = useNavigate();

  function handleOpenCreate() {
    if (onOpenCreate) {
      onOpenCreate();
      return;
    }

    const handle = CreateAccountOverlay.open();
    handle.closed.then((outcome) => {
      if (outcome.status === 'completed') {
        navigate({
          to: '/app/accounts/$accountId',
          params: { accountId: outcome.value.id },
          replace: true
        });
      }
    });
  }

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
        onClick={handleOpenCreate}>
        Create Account
      </Button>
    </div>
  );
}
