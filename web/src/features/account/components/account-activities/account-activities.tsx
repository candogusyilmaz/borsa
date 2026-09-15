import { Alert, Skeleton, Stack, Text } from '@mantine/core';
import { $api } from '@/api/client';
import { registerOverlay } from '@/shared/overlay';
import { ActivityHistoryCard } from '../activity-history-card/activity-history-card';

interface AccountActivitiesProps {
  accountId: string;
  accountName?: string;
}

export function AccountActivities({ accountId }: AccountActivitiesProps) {
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

  return (
    <div style={{ paddingBottom: '1rem' }}>
      <ActivityHistoryCard account={accountQuery.data} />
    </div>
  );
}

export const AccountActivitiesOverlay = registerOverlay(AccountActivities, {
  name: 'account-activities',
  title: (props) => `Cash Activity — ${props.accountName ?? 'Account'}`,
  presentation: 'drawer',
  desktopSize: '560px'
});
