import { useDisclosure } from '@mantine/hooks';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { FinancialAccount } from '../../types';
import { AccountActionsDrawer } from '../account-actions-drawer';
import { AccountActivitiesDrawer } from '../account-activities-drawer';
import { BalanceLiquidityCard, RecentActivityCard, StartingBalanceCoverageCard } from '../account-detail-stack';
import { AccountInfoDrawer } from '../account-info-drawer';
import { AccountQuickActions } from '../account-quick-actions';
import { AccountSettingsModal } from '../account-settings-modal/account-settings-modal';
import { AccountsListSection } from '../accounts-list-section';
import { ArchiveAccountModal } from '../archive-account-modal/archive-account-modal';
import { OpeningCorrectionModal } from '../opening-correction-modal/opening-correction-modal';
import { RecordCashActivityModal } from '../record-cash-activity-modal/record-cash-activity-modal';
import { TransferModal } from '../transfer-modal/transfer-modal';
import classes from './account-detail-content.module.css';

export interface AccountDetailContentProps {
  account: FinancialAccount;
  allAccounts: FinancialAccount[];
  onOpenAccountPicker: () => void;
  onRefetchAll?: () => Promise<unknown>;
  onAccountArchived?: () => Promise<void> | void;
}

export function AccountDetailContent({
  account,
  allAccounts,
  onOpenAccountPicker,
  onRefetchAll,
  onAccountArchived
}: AccountDetailContentProps) {
  const queryClient = useQueryClient();

  // Accordion stack expansion state (independent cards, default all collapsed)
  const [balanceExpanded, setBalanceExpanded] = useState(false);
  const [startingExpanded, setStartingExpanded] = useState(false);
  const [activityExpanded, setActivityExpanded] = useState(false);

  // Modals and Drawers disclosures
  const [transferModalOpened, { open: openTransferModal, close: closeTransferModal }] = useDisclosure(false);
  const [depositModalOpened, { open: openDepositModal, close: closeDepositModal }] = useDisclosure(false);
  const [withdrawModalOpened, { open: openWithdrawModal, close: closeWithdrawModal }] = useDisclosure(false);
  const [settingsModalOpened, { open: openSettingsModal, close: closeSettingsModal }] = useDisclosure(false);
  const [archiveModalOpened, { open: openArchiveModal, close: closeArchiveModal }] = useDisclosure(false);
  const [openingCorrectionOpened, { open: openOpeningCorrection, close: closeOpeningCorrection }] = useDisclosure(false);
  const [actionsDrawerOpened, { open: openActionsDrawer, close: closeActionsDrawer }] = useDisclosure(false);
  const [infoDrawerOpened, { open: openInfoDrawer, close: closeInfoDrawer }] = useDisclosure(false);
  const [activitiesDrawerOpened, { open: openActivitiesDrawer, close: closeActivitiesDrawer }] = useDisclosure(false);

  // Invalidate queries tied to this account or accounts list
  async function handleRefreshSelectedAccount() {
    if (!account.id) return;
    queryClient.invalidateQueries({
      predicate: (query) => {
        const key = JSON.stringify(query.queryKey);
        return key.includes(account.id);
      }
    });
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
    if (onRefetchAll) {
      await onRefetchAll();
    }
  }

  return (
    <div className={classes.detailContentFlow}>
      {/* 1. Quick Actions */}
      <AccountQuickActions
        account={account}
        onDeposit={openDepositModal}
        onWithdraw={openWithdrawModal}
        onTransfer={openTransferModal}
        onMore={openActionsDrawer}
      />

      {/* 2. Collapsible Detail Cards Stack */}
      <div className={classes.stackSection}>
        {/* Card 1: Balance & Liquidity */}
        <BalanceLiquidityCard account={account} expanded={balanceExpanded} onToggle={() => setBalanceExpanded((prev) => !prev)} />

        {/* Card 2: Starting Balance & Coverage */}
        <StartingBalanceCoverageCard
          account={account}
          expanded={startingExpanded}
          onToggle={() => setStartingExpanded((prev) => !prev)}
          onOpenCorrection={openOpeningCorrection}
        />

        {/* Card 3: Recent Activity */}
        <RecentActivityCard
          account={account}
          expanded={activityExpanded}
          onToggle={() => setActivityExpanded((prev) => !prev)}
          onViewAll={openActivitiesDrawer}
        />
      </div>

      {/* 3. Single-line View all accounts action */}
      <AccountsListSection accounts={allAccounts} onOpenAccountPicker={onOpenAccountPicker} />

      {/* 4. Modals & Drawers */}
      {/* Transfer Modal */}
      <TransferModal
        key={`transfer-${account.id}`}
        opened={transferModalOpened}
        onClose={closeTransferModal}
        defaultSourceAccountId={account.id}
        onSuccess={handleRefreshSelectedAccount}
      />

      {/* Deposit Cash Modal */}
      <RecordCashActivityModal
        key={`deposit-${account.id}`}
        account={account}
        opened={depositModalOpened}
        onClose={closeDepositModal}
        defaultType="CASH_DEPOSIT"
        onSuccess={handleRefreshSelectedAccount}
      />

      {/* Withdraw Cash Modal */}
      <RecordCashActivityModal
        key={`withdraw-${account.id}`}
        account={account}
        opened={withdrawModalOpened}
        onClose={closeWithdrawModal}
        defaultType="CASH_WITHDRAWAL"
        onSuccess={handleRefreshSelectedAccount}
      />

      {/* Account Actions Drawer (More) */}
      <AccountActionsDrawer
        account={account}
        opened={actionsDrawerOpened}
        onClose={closeActionsDrawer}
        onRefresh={handleRefreshSelectedAccount}
        onOpenSettings={openSettingsModal}
        onOpenInfo={openInfoDrawer}
        onOpenArchive={openArchiveModal}
      />

      {/* Account Specifications & Invariants Drawer */}
      <AccountInfoDrawer account={account} opened={infoDrawerOpened} onClose={closeInfoDrawer} />

      {/* Account Settings Modal */}
      <AccountSettingsModal
        key={`settings-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={settingsModalOpened}
        onClose={closeSettingsModal}
        onRefetchAccount={handleRefreshSelectedAccount}
      />

      {/* Archive Account Confirmation Modal */}
      <ArchiveAccountModal
        key={`archive-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={archiveModalOpened}
        onClose={closeArchiveModal}
        onRefetchAccount={handleRefreshSelectedAccount}
        onSuccess={async () => {
          if (onAccountArchived) {
            await onAccountArchived();
          } else {
            await handleRefreshSelectedAccount();
          }
        }}
      />

      {/* Opening Balance Correction Modal */}
      <OpeningCorrectionModal
        key={`opening-correction-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={openingCorrectionOpened}
        onClose={closeOpeningCorrection}
        onRefetchAccount={handleRefreshSelectedAccount}
      />

      {/* Full Activity History Drawer */}
      <AccountActivitiesDrawer
        account={account}
        opened={activitiesDrawerOpened}
        onClose={closeActivitiesDrawer}
        onOpenDeposit={openDepositModal}
        onOpenWithdraw={openWithdrawModal}
        onOpenTransfer={openTransferModal}
        onActivityUpdated={handleRefreshSelectedAccount}
      />
    </div>
  );
}
