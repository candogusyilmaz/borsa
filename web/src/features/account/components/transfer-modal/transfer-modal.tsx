import { Badge, Group, Modal, Text } from '@mantine/core';
import { ArrowsLeftRightIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import type { ActivityResponse } from '../../types';
import { TransferSession } from './transfer-session';
import type { TransferHeaderInfo } from './transfer-types';

export interface TransferModalProps {
  opened: boolean;
  onClose: () => void;
  defaultSourceAccountId?: string;
  defaultDestinationAccountId?: string;
  lockSourceAccount?: boolean;
  onSuccess?: (activity: ActivityResponse) => void;
}

export function TransferModal({ opened, onClose, ...props }: TransferModalProps) {
  const [sessionId, setSessionId] = useState(0);
  const [headerInfo, setHeaderInfo] = useState<TransferHeaderInfo>({ step: 'edit' });

  const title =
    headerInfo.step === 'success' ? 'Transfer Completed' : headerInfo.step === 'preview' ? 'Review & Confirm Transfer' : 'Transfer Funds';

  const handleClose = () => {
    setHeaderInfo({ step: 'edit' });
    onClose();
  };

  const handleStartAnother = () => {
    setHeaderInfo({ step: 'edit' });
    setSessionId((id) => id + 1);
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={
        <Group gap="xs">
          <ArrowsLeftRightIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
          <Text fw={700} size="md">
            {title}
          </Text>
          {headerInfo.currency && headerInfo.step !== 'success' && (
            <Badge color="teal" variant="light" size="sm">
              {headerInfo.currency}
            </Badge>
          )}
        </Group>
      }
      size="lg"
      centered
      radius="md">
      {opened && (
        <TransferSession
          key={sessionId}
          {...props}
          onClose={handleClose}
          onStartAnother={handleStartAnother}
          onHeaderChange={setHeaderInfo}
        />
      )}
    </Modal>
  );
}
