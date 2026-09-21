import { Alert, Skeleton, Stack, Text } from '@mantine/core';
import { $api } from '@/api/client';
import { registerOverlay } from '@/shared/overlay';
import { ActivityDetailOverlay } from '../activity-detail/activity-detail';
import { ActivityHistoryCard } from '../activity-history-card/activity-history-card';
import { CashActivityOverlay } from '../record-cash-activity';
import { TransferOverlay } from '../transfer/transfer';

interface AccountActivitiesProps {
  accountId: string;
  accountName?: string;
}

export function AccountActivities({ accountId }: AccountActivitiesProps) {
  const current = AccountActivitiesOverlay.useCurrent();
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={52} radius="sm" />
        <Skeleton height={52} radius="sm" />
        <Skeleton height={52} radius="sm" />
      </Stack>
    );
  }

  if (accountQuery.isError || !accountQuery.data) {
    return (
      <Alert title="Could not load account activity" color="red" variant="light" m="md">
        <Text size="sm">The requested account could not be loaded.</Text>
      </Alert>
    );
  }

  const account = accountQuery.data;

  return (
    <div style={{ paddingBottom: '1rem' }}>
      <ActivityHistoryCard
        account={account}
        onOpenDetail={(id, isAlreadyReversed) =>
          current.push(ActivityDetailOverlay, {
            activityId: id,
            isAccountArchived: account.archived,
            isAlreadyReversed
          })
        }
        onDepositCash={() =>
          current.push(CashActivityOverlay, {
            accountId: account.id,
            defaultType: 'CASH_DEPOSIT'
          })
        }
        onWithdrawCash={() =>
          current.push(CashActivityOverlay, {
            accountId: account.id,
            defaultType: 'CASH_WITHDRAWAL'
          })
        }
        onRecordFee={() =>
          current.push(CashActivityOverlay, {
            accountId: account.id,
            defaultType: 'CASH_FEE'
          })
        }
        onAddInterest={() =>
          current.push(CashActivityOverlay, {
            accountId: account.id,
            defaultType: 'CASH_INTEREST_CREDIT'
          })
        }
        onTransferFunds={() =>
          current.push(TransferOverlay, {
            defaultSourceAccountId: account.id
          })
        }
      />
    </div>
  );
}

export const AccountActivitiesOverlay = registerOverlay(AccountActivities, {
  name: 'account-activities',
  title: (props) => `Cash Activity — ${props.accountName ?? 'Account'}`,
  presentation: 'drawer',
  desktopSize: '560px'
});
