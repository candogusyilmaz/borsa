import { Button, type ButtonProps } from '@mantine/core';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';

type SellStockModalProps = {
  stockId: string;
  buttonProps?: ButtonProps;
};

export function SellStockModal({ stockId, buttonProps }: SellStockModalProps) {
  const openModal = useTransactionModalStore((state) => state.open);

  return (
    <Button variant="subtle" size="compact-xs" color="red.7" fw={400} fz="xs" {...buttonProps} onClick={() => openModal('Sell', stockId)}>
      Sell
    </Button>
  );
}
