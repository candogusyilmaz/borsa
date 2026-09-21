import { Group, Text } from '@mantine/core';
import { ArrowsLeftRightIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { registerOverlay } from '@/shared/overlay';
import { ActivityDetailOverlay } from '../activity-detail/activity-detail';
import { TransferSession } from './transfer-session';

export interface TransferOverlayProps {
  defaultSourceAccountId?: string;
  defaultDestinationAccountId?: string;
  lockSourceAccount?: boolean;
}

export function Transfer({ ...props }: TransferOverlayProps) {
  const current = TransferOverlay.useCurrent();
  const [sessionId, setSessionId] = useState(0);

  const handleClose = () => {
    current.dismiss('cancelled');
  };

  const handleStartAnother = () => {
    setSessionId((id) => id + 1);
  };

  const handleViewActivity = (activityId: string) => {
    current.replace(ActivityDetailOverlay, { activityId });
  };

  return (
    <TransferSession
      key={sessionId}
      {...props}
      onClose={handleClose}
      onStartAnother={handleStartAnother}
      onSetTitle={current.setTitle}
      onViewActivity={handleViewActivity}
    />
  );
}

export const TransferOverlay = registerOverlay(Transfer, {
  name: 'transfer',
  title: (
    <Group gap="xs">
      <ArrowsLeftRightIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Transfer Funds
      </Text>
    </Group>
  ),
  presentation: 'drawer',
  size: 'lg'
});
