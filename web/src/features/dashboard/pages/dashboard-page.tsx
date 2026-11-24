import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, Group, Menu, Skeleton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconChevronDown, IconDots, IconPlus, IconTrash, IconTrendingUp } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '~/api/openapi';
import { AccountMenu } from '~/app/shell/account-menu';
import { isDataStale } from '~/features/positions';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { DashboardPortfolios } from '../components/dashboard-portfolios';
import { DashboardSummary } from '../components/dashboard-summary';
import { TransactionsChart } from '../components/transactions-chart';
import { type DashboardModel, useDashboardView } from '../hooks/use-dashboard-view';
import { useCreateNewDashboardModalStore } from '../workflows/create-dashboard-modal';
import styles from './dashboard-page.module.css';

export function DashboardPage() {
  const { t } = useLingui();
  const model = useDashboardView();
  const dashboard = model.dashboard.data;
  const isMobile = useIsMobile();
  const deletable = model.selected?.isDefault === false ? model.selected.id : undefined;

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.pageContext}>
          <DashboardSelector model={model} isMobile={isMobile} />
          {deletable && <DashboardOptions key={deletable} dashboardId={deletable} name={model.selected?.name || t`Dashboard`} />}
        </div>
        {isMobile ? <AccountMenu isMobile /> : model.details && <MarketStatus market={model.details.market} />}
      </header>
      {!model.selected && (
        <p className={styles.state}>
          <Trans>Create a dashboard to track your balance and realized performance.</Trans>
        </p>
      )}
      {model.selected && model.dashboard.isPending && <Skeleton h={154} aria-label={t`Loading dashboard`} />}
      {model.selected && model.dashboard.isError && (
        <div className={styles.state}>
          <p role="alert">
            <Trans>Could not load this dashboard.</Trans>
          </p>
          <Button variant="default" onClick={() => void model.dashboard.refetch()}>
            <Trans>Try again</Trans>
          </Button>
        </div>
      )}
      {dashboard && (
        <>
          <DashboardSummary dashboard={dashboard} realized={model.realized} />
          <TransactionsChart model={model} currency={dashboard.currencyCode} />
          {model.details && (
            <DashboardPortfolios
              key={dashboard.id}
              details={model.details}
              pending={model.trades.isPending || model.portfolios.isPending || model.positions.isPending}
              unavailable={model.trades.isError || model.portfolios.isError || model.positions.isError}
            />
          )}
        </>
      )}
    </div>
  );
}

function DashboardSelector({ model, isMobile }: { model: DashboardModel; isMobile: boolean }) {
  const { t } = useLingui();
  const openCreate = useCreateNewDashboardModalStore((store) => store.open);
  const [opened, picker] = useDisclosure();
  const fallback = t`Dashboard`;
  const title = model.selected?.name || fallback;

  function create() {
    picker.close();
    openCreate();
  }

  function select(id?: string) {
    model.setSelectedId(id);
    picker.close();
  }

  const titleLabel = (
    <>
      {isMobile && (
        <span className={styles.titleMark} aria-hidden="true">
          <IconTrendingUp size={13} stroke={2.4} />
        </span>
      )}
      <span className={styles.titleText}>{title}</span>
      <IconChevronDown size={16} stroke={1.6} aria-hidden="true" />
    </>
  );

  if (isMobile) {
    return (
      <>
        <h1 className={styles.title}>
          <button
            type="button"
            className={styles.titleButton}
            onClick={picker.open}
            aria-haspopup="dialog"
            aria-expanded={opened}
            title={title}>
            {titleLabel}
          </button>
        </h1>
        <ResponsiveOverlay opened={opened} onClose={picker.close} title={t`Dashboards`}>
          <ul className={styles.picker}>
            {model.dashboards.map(({ id, name }) => (
              <li key={id}>
                <button type="button" aria-current={id === model.selected?.id || undefined} onClick={() => select(id)}>
                  {name || fallback}
                  {id === model.selected?.id && <IconCheck size={16} aria-hidden="true" />}
                </button>
              </li>
            ))}
          </ul>
          <Button fullWidth radius="xl" leftSection={<IconPlus size={16} />} onClick={create}>
            <Trans>New dashboard</Trans>
          </Button>
        </ResponsiveOverlay>
      </>
    );
  }

  return (
    <h1 className={styles.title}>
      <Menu opened={opened} onChange={picker.set} position="bottom-start" width={288} radius="lg">
        <Menu.Target>
          <button type="button" className={styles.titleButton}>
            {titleLabel}
          </button>
        </Menu.Target>
        <Menu.Dropdown>
          {model.dashboards.length > 0 && (
            <Menu.Label>
              <Trans>Dashboards</Trans>
            </Menu.Label>
          )}
          {model.dashboards.map(({ id, name }) => (
            <Menu.Item
              key={id}
              onClick={() => select(id)}
              aria-current={id === model.selected?.id || undefined}
              rightSection={id === model.selected?.id && <IconCheck size={16} aria-hidden="true" />}>
              {name || fallback}
            </Menu.Item>
          ))}
          {model.dashboards.length > 0 && <Menu.Divider />}
          <Menu.Item leftSection={<IconPlus size={16} />} onClick={create}>
            <Trans>New dashboard</Trans>
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </h1>
  );
}

function DashboardOptions({ dashboardId, name }: { dashboardId: string; name: string }) {
  const { t } = useLingui();
  const [opened, confirm] = useDisclosure();
  const client = useQueryClient();
  const deletion = $api.useMutation('delete', '/api/dashboards/{dashboardId}', {
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['get', '/api/dashboards'] });
      confirm.close();
    }
  });

  function askToDelete() {
    deletion.reset();
    confirm.open();
  }

  return (
    <>
      <Menu position="bottom-start" radius="md">
        <Menu.Target>
          <ActionIcon variant="subtle" color="gray" aria-label={t`Dashboard options`}>
            <IconDots size={17} aria-hidden="true" />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item color="loss" leftSection={<IconTrash size={15} />} onClick={askToDelete}>
            <Trans>Delete dashboard</Trans>
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      <ResponsiveOverlay
        opened={opened}
        onClose={() => {
          if (!deletion.isPending) confirm.close();
        }}
        closeOnClickOutside={!deletion.isPending}
        title={t`Delete dashboard`}>
        <p>
          <Trans>Delete the “{name}” dashboard? Its portfolios and transactions will be kept.</Trans>
        </p>
        {deletion.isError && (
          <p role="alert">
            <Trans>Could not delete the dashboard. Try again.</Trans>
          </p>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={confirm.close} disabled={deletion.isPending}>
            <Trans>Cancel</Trans>
          </Button>
          <Button
            color="loss"
            loading={deletion.isPending}
            onClick={() => deletion.mutate({ params: { path: { dashboardId: Number(dashboardId) } } })}>
            <Trans>Delete dashboard</Trans>
          </Button>
        </Group>
      </ResponsiveOverlay>
    </>
  );
}

function MarketStatus({ market }: { market: NonNullable<DashboardModel['details']>['market'] }) {
  const { t } = useLingui();
  const format = useFormat();
  const quote = market.updatedAt == null ? undefined : new Date(market.updatedAt);
  const fresh = quote != null && !isDataStale(quote);
  const status = quote ? (fresh ? t`Prices updated` : t`Last quote`) : t`Quotes pending`;

  return (
    <p className={styles.market} data-fresh={fresh || undefined}>
      {t(market.label)} ·{' '}
      {quote ? (
        <time dateTime={quote.toISOString()} title={format.toShortDateTime(quote)}>
          {status}
        </time>
      ) : (
        status
      )}
    </p>
  );
}
