import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { useParams } from '@tanstack/react-router';
import clsx from 'clsx';
import { useTransactionModalStore } from '~/features/trades/workflows/transaction-modal';
import styles from './add-trade-button.module.css';

export function AddTradeButton({ compact, className }: { compact?: boolean; className?: string }) {
  const { t } = useLingui();
  const { portfolioId } = useParams({ strict: false });
  const open = useTransactionModalStore((state) => state.open);

  function start() {
    open('Buy', undefined, portfolioId);
  }

  if (compact) {
    return (
      <ActionIcon
        variant="filled"
        aria-label={t({ comment: 'Accessible label of the compact action to record a buy or sell', message: 'Add trade' })}
        onClick={start}>
        <IconPlus size={19} />
      </ActionIcon>
    );
  }

  return (
    <Button className={clsx(styles.addTrade, className)} radius={8} leftSection={<IconPlus size={15} />} onClick={start}>
      <Trans comment="Primary action to record a new buy or sell transaction">Add Trade</Trans>
    </Button>
  );
}
