import { Badge, Group, Text } from '@mantine/core';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';
import { $api } from '@/api/client';
import { RecordCashActivityForm } from '@/features/account/components/record-cash-activity-modal/record-cash-activity-modal';
import { isCashFundingCapable } from '@/features/account/utils/account-formatters';
import { RouteDrawer } from '@/shared/components/route-drawer';

export const Route = createFileRoute('/app/accounts/$accountId/withdraw')({
  component: WithdrawDrawerRoute
});

function WithdrawDrawerRoute() {
  const { accountId } = Route.useParams();
  const { data: account } = $api.useQuery('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId } } });
  const navigate = useNavigate();
  const canCashTransact = account && account.trackingMode !== 'HOLDINGS_ONLY' && isCashFundingCapable(account.kind) && !account.archived;

  useEffect(() => {
    if (account && !canCashTransact) {
      navigate({ to: '..', params: { accountId }, replace: true, resetScroll: false });
    }
  }, [account, accountId, canCashTransact, navigate]);

  // The parent route renders its loading/not-found state before this outlet.
  if (!account || !canCashTransact) return null;

  function leaveRoute() {
    navigate({ to: '..', params: { accountId }, replace: true, resetScroll: false });
  }

  return (
    <RouteDrawer
      title={
        <Group gap="xs">
          <Text fw={700} size="md">
            Record Cash Activity
          </Text>
          <Badge color="teal" variant="light" size="sm">
            {account.currency}
          </Badge>
        </Group>
      }
      desktopSize="480px"
      onExited={leaveRoute}>
      {({ close }) => <RecordCashActivityForm account={account} defaultType="CASH_WITHDRAWAL" onClose={close} />}
    </RouteDrawer>
  );
}
