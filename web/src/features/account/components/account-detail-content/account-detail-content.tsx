import { useDisclosure } from '@mantine/hooks';
import type { FinancialAccount } from '../../types';
import { useAccountDetailOverlay } from '../account-detail-overlay/account-detail-overlay-provider';
import { BalanceLiquidityCard, RecentActivityCard, StartingBalanceCoverageCard } from '../account-detail-stack';
import { AccountQuickActions } from '../account-quick-actions';
import { AccountsListSection } from '../accounts-list-section';
import classes from './account-detail-content.module.css';

export interface AccountDetailContentProps {
  account: FinancialAccount;
  allAccounts: FinancialAccount[];
  onOpenAccountPicker: () => void;
}

function AccountDetailStack({ account }: { account: FinancialAccount }) {
  const { open } = useAccountDetailOverlay();
  const [balanceExpanded, { toggle: toggleBalance }] = useDisclosure(false);
  const [startingExpanded, { toggle: toggleStarting }] = useDisclosure(false);
  const [activityExpanded, { toggle: toggleActivity }] = useDisclosure(false);

  return (
    <div className={classes.stackSection}>
      <BalanceLiquidityCard account={account} expanded={balanceExpanded} onToggle={toggleBalance} />
      <StartingBalanceCoverageCard
        account={account}
        expanded={startingExpanded}
        onToggle={toggleStarting}
        onOpenCorrection={() => open({ type: 'opening-correction' })}
      />
      <RecentActivityCard
        account={account}
        expanded={activityExpanded}
        onToggle={toggleActivity}
        onViewAll={() => open({ type: 'activities' })}
      />
    </div>
  );
}

export function AccountDetailContent({ account, allAccounts, onOpenAccountPicker }: AccountDetailContentProps) {
  return (
    <div className={classes.detailContentFlow}>
      <AccountQuickActions account={account} />
      <AccountDetailStack account={account} />
      <AccountsListSection accounts={allAccounts} onOpenAccountPicker={onOpenAccountPicker} />
    </div>
  );
}
