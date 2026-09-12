import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import type { FinancialAccount } from '../../types';
import { ActivityHistoryCard } from '../activity-history-card/activity-history-card';

interface AccountActivitiesDrawerProps {
  account: FinancialAccount;
  opened: boolean;
  onClose: () => void;
  onOpenDeposit: () => void;
  onOpenWithdraw: () => void;
  onOpenTransfer: () => void;
  onActivityUpdated?: () => void;
}

export function AccountActivitiesDrawer({
  account,
  opened,
  onClose,
  onOpenDeposit,
  onOpenWithdraw,
  onOpenTransfer,
  onActivityUpdated
}: AccountActivitiesDrawerProps) {
  return (
    <ResponsiveDrawer opened={opened} onClose={onClose} desktopSize="560px" title={`Cash Activity — ${account.name}`}>
      <div style={{ paddingBottom: '1rem' }}>
        <ActivityHistoryCard
          account={account}
          onOpenDeposit={onOpenDeposit}
          onOpenWithdraw={onOpenWithdraw}
          onOpenTransfer={onOpenTransfer}
          onActivityUpdated={onActivityUpdated}
        />
      </div>
    </ResponsiveDrawer>
  );
}
