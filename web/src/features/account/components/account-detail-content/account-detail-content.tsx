import { useDisclosure } from '@mantine/hooks';
import type { FinancialAccount } from '../../types';
import { AccountActivitiesOverlay } from '../account-activities/account-activities';
import { BalanceLiquidityCard, RecentActivityCard, StartingBalanceCoverageCard } from '../account-detail-stack';
import { AccountQuickActions } from '../account-quick-actions';
import { AccountsListSection } from '../accounts-list-section';
import { OpeningCorrectionOverlay } from '../opening-correction/opening-correction';
import classes from './account-detail-content.module.css';

export interface AccountDetailContentProps {
  account: FinancialAccount;
  allAccounts: FinancialAccount[];
  onOpenAccountPicker: () => void;
  onAccountArchived?: () => Promise<void>;
}

function AccountDetailStack({ account }: { account: FinancialAccount }) {
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
        onOpenCorrection={() => OpeningCorrectionOverlay.open({ accountId: account.id })}
      />
      <RecentActivityCard
        account={account}
        expanded={activityExpanded}
        onToggle={toggleActivity}
        onViewAll={() => AccountActivitiesOverlay.open({ accountId: account.id, accountName: account.name })}
      />
    </div>
  );
}

export function AccountDetailContent({ account, allAccounts, onOpenAccountPicker, onAccountArchived }: AccountDetailContentProps) {
  return (
    <div className={classes.detailContentFlow}>
      <AccountQuickActions account={account} onAccountArchived={onAccountArchived} />
      <AccountDetailStack account={account} />
      <AccountsListSection accounts={allAccounts} onOpenAccountPicker={onOpenAccountPicker} />
    </div>
  );
}
