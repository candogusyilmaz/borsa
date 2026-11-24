import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useParams } from '@tanstack/react-router';
import clsx from 'clsx';
import { $api } from '~/api/openapi';
import { useTransactionModalStore } from '~/features/trades/workflows/transaction-modal';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import styles from './add-trade-button.module.css';

export function AddTradeButton({ compact, className }: { compact?: boolean; className?: string }) {
  const { t } = useLingui();
  const { portfolioId } = useParams({ strict: false });
  const open = useTransactionModalStore((state) => state.open);
  const [opened, picker] = useDisclosure();
  const portfolios = $api.useQuery('get', '/api/portfolios', {}, { enabled: opened });

  function addTo(id: string) {
    picker.close();
    open('Buy', undefined, id);
  }

  function start() {
    if (portfolioId) addTo(portfolioId);
    else picker.open();
  }

  return (
    <>
      {compact ? (
        <ActionIcon
          variant="filled"
          aria-label={t({ comment: 'Accessible label of the compact action to record a buy or sell', message: 'Add trade' })}
          onClick={start}>
          <IconPlus size={19} />
        </ActionIcon>
      ) : (
        <Button className={clsx(styles.addTrade, className)} radius={8} leftSection={<IconPlus size={15} />} onClick={start}>
          <Trans comment="Primary action to record a new buy or sell transaction">Add Trade</Trans>
        </Button>
      )}
      <ResponsiveOverlay opened={opened} onClose={picker.close} title={t`Choose a portfolio`}>
        {portfolios.isPending && (
          <output>
            <Trans>Loading portfolios…</Trans>
          </output>
        )}
        {portfolios.isError && (
          <p role="alert">
            <Trans>Could not load portfolios.</Trans>
          </p>
        )}
        {portfolios.data?.length === 0 && (
          <p>
            <Trans>Create a portfolio from the Portfolios menu to start trading.</Trans>
          </p>
        )}
        <ul className={styles.portfolioOptions}>
          {portfolios.data?.map(({ id, name }) => (
            <li key={id}>
              <button type="button" onClick={() => addTo(id)}>
                {name}
              </button>
            </li>
          ))}
        </ul>
      </ResponsiveOverlay>
    </>
  );
}
