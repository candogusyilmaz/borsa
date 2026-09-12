import { ArrowDownLeftIcon, ArrowsLeftRightIcon, ArrowUpRightIcon, DotsThreeIcon } from '@phosphor-icons/react';
import type { FinancialAccount } from '../../types';
import { isCashFundingCapable } from '../../utils/account-formatters';
import classes from './account-quick-actions.module.css';

interface AccountQuickActionsProps {
  account: FinancialAccount;
  onDeposit: () => void;
  onWithdraw: () => void;
  onTransfer: () => void;
  onMore: () => void;
}

export function AccountQuickActions({ account, onDeposit, onWithdraw, onTransfer, onMore }: AccountQuickActionsProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';
  const canCashTransact = !isHoldings && isCashFundingCapable(account.kind) && !account.archived;

  return (
    <nav className={classes.actionsGrid} aria-label="Account quick actions">
      {/* 1. Deposit */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.depositBtn}`}
        onClick={onDeposit}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Deposit into ${account.name}` : 'Deposit not available for this account'}>
        <ArrowDownLeftIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Deposit</span>
      </button>

      {/* 2. Withdraw */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.withdrawBtn}`}
        onClick={onWithdraw}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Withdraw from ${account.name}` : 'Withdrawal not available for this account'}>
        <ArrowUpRightIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Withdraw</span>
      </button>

      {/* 3. Transfer */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.transferBtn}`}
        onClick={onTransfer}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Transfer funds from ${account.name}` : 'Transfer not available for this account'}>
        <ArrowsLeftRightIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Transfer</span>
      </button>

      {/* 4. More */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.moreBtn}`}
        onClick={onMore}
        aria-label={`More actions for ${account.name}`}>
        <DotsThreeIcon size={22} weight="bold" />
        <span className={classes.btnLabel}>More</span>
      </button>
    </nav>
  );
}
