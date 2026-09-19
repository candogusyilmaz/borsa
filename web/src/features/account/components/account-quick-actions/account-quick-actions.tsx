import { ArrowDownLeftIcon, ArrowsLeftRightIcon, ArrowUpRightIcon, DotsThreeIcon, ReceiptIcon, TrendUpIcon } from '@phosphor-icons/react';
import type { FinancialAccount } from '../../types';
import { isCashFundingCapable } from '../../utils/account-formatters';
import { AccountActionsOverlay } from '../account-actions';
import { CashActivityOverlay } from '../record-cash-activity';
import { TransferOverlay } from '../transfer/transfer';
import classes from './account-quick-actions.module.css';

interface AccountQuickActionsProps {
  account: FinancialAccount;
  onAccountArchived?: () => Promise<void>;
}

export function AccountQuickActions({ account, onAccountArchived }: AccountQuickActionsProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';
  const canCashTransact = !isHoldings && isCashFundingCapable(account.kind) && !account.archived;

  return (
    <nav className={classes.actionsGrid} aria-label="Account quick actions">
      {/* 1. Deposit */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.depositBtn}`}
        onClick={() => CashActivityOverlay.open({ accountId: account.id, defaultType: 'CASH_DEPOSIT' })}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Deposit into ${account.name}` : 'Deposit not available for this account'}>
        <ArrowDownLeftIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Deposit</span>
      </button>

      {/* 2. Withdraw */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.withdrawBtn}`}
        onClick={() => CashActivityOverlay.open({ accountId: account.id, defaultType: 'CASH_WITHDRAWAL' })}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Withdraw from ${account.name}` : 'Withdrawal not available for this account'}>
        <ArrowUpRightIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Withdraw</span>
      </button>

      {/* 3. Fee */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.feeBtn}`}
        onClick={() => CashActivityOverlay.open({ accountId: account.id, defaultType: 'CASH_FEE' })}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Record a fee for ${account.name}` : 'Fees not available for this account'}>
        <ReceiptIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Fee</span>
      </button>

      {/* 4. Interest */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.interestBtn}`}
        onClick={() => CashActivityOverlay.open({ accountId: account.id, defaultType: 'CASH_INTEREST_CREDIT' })}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Record interest for ${account.name}` : 'Interest is not available for this account'}>
        <TrendUpIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Interest</span>
      </button>

      {/* 5. Transfer */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.transferBtn}`}
        onClick={() => TransferOverlay.open({ defaultSourceAccountId: account.id })}
        disabled={!canCashTransact}
        aria-label={canCashTransact ? `Transfer funds from ${account.name}` : 'Transfer not available for this account'}>
        <ArrowsLeftRightIcon size={20} weight="bold" />
        <span className={classes.btnLabel}>Transfer</span>
      </button>

      {/* 6. More */}
      <button
        type="button"
        className={`${classes.actionBtn} ${classes.moreBtn}`}
        onClick={() => AccountActionsOverlay.open({ accountId: account.id, onAccountArchived })}
        aria-label={`More actions for ${account.name}`}>
        <DotsThreeIcon size={22} weight="bold" />
        <span className={classes.btnLabel}>More</span>
      </button>
    </nav>
  );
}
