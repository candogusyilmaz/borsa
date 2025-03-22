import { Button, type ButtonProps } from '@mantine/core';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';

type BuyStockModalProps = {
  stockId: string;
  buttonProps?: ButtonProps;
};

export function BuyStockModal({ stockId, buttonProps }: BuyStockModalProps) {
  const openModal = useTransactionModalStore((state) => state.open);

  return (
    <Button variant="subtle" size="compact-xs" color="teal.7" fw={400} fz="xs" {...buttonProps} onClick={() => openModal('Buy', stockId)}>
      Buy
    </Button>
  );
}
