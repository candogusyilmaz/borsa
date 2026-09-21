import { Group, Text } from '@mantine/core';
import { TrendUpIcon } from '@phosphor-icons/react';
import { registerOverlay } from '@/shared/overlay';
import { TradeDetailOverlay } from '../trade-detail/trade-detail';
import { TradeForm } from './trade-form';
import { TradePreview } from './trade-preview';
import { TradeSuccess } from './trade-success';
import type { RecordTradeProps } from './trade-types';
import { useTradeSession } from './use-trade-session';

export function RecordTrade(props: RecordTradeProps) {
  const current = TradeOverlay.useCurrent();
  const session = useTradeSession(props);

  const handleClose = () => {
    current.dismiss('cancelled');
  };

  const handleViewDetails = (activityId: string) => {
    current.replace(TradeDetailOverlay, { activityId });
  };

  return (
    <>
      {session.state.step === 'edit' && <TradeForm session={session} options={props} onCancel={handleClose} />}
      {session.state.step === 'preview' && <TradePreview session={session} />}
      {session.state.step === 'success' && <TradeSuccess session={session} onClose={handleClose} onViewDetails={handleViewDetails} />}
    </>
  );
}

export const TradeOverlay = registerOverlay(RecordTrade, {
  name: 'record-trade',
  title: (
    <Group gap="xs">
      <TrendUpIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Record Brokerage Trade
      </Text>
    </Group>
  ),
  presentation: 'drawer',
  size: 'lg'
});
