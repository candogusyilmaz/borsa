import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, Menu, Skeleton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconChevronDown, IconPlus } from '@tabler/icons-react';
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
  const portfolioCount = model.details?.knownPortfolioCount;

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <div>
          <DashboardSelector model={model} />
          {model.selected && (
            <p className={styles.pageMeta}>
              {portfolioCount == null ? (
                <Trans>Portfolios with recorded activity</Trans>
              ) : (
                <Plural value={portfolioCount} one="# portfolio with recorded activity" other="# portfolios with recorded activity" />
              )}
            </p>
          )}
        </div>
        {model.details && <MarketStatus market={model.details.market} />}
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

function DashboardSelector({ model }: { model: DashboardModel }) {
  const { t } = useLingui();
  const isMobile = useIsMobile();
  const openCreate = useCreateNewDashboardModalStore((store) => store.open);
  const [opened, picker] = useDisclosure();
  const title = model.selected?.name || t`Dashboard`;

  function create() {
    picker.close();
    openCreate();
  }

  function select(id?: string) {
    model.setSelectedId(id);
    picker.close();
  }

  const choices = model.dashboards.filter(({ id }) => !!id);
  if (isMobile) {
    return (
      <>
        <h1 className={styles.title}>
          <button type="button" className={styles.titleButton} onClick={picker.open} aria-haspopup="dialog">
            {title}
            <IconChevronDown size={16} stroke={1.6} aria-hidden="true" />
          </button>
        </h1>
        <ResponsiveOverlay opened={opened} onClose={picker.close} title={t`Dashboards`}>
          <ul className={styles.picker}>
            {choices.map(({ id, name }) => (
              <li key={id}>
                <button type="button" aria-current={id === model.selected?.id || undefined} onClick={() => select(id)}>
                  {name}
                  {id === model.selected?.id && <IconCheck size={16} aria-hidden="true" />}
                </button>
              </li>
            ))}
          </ul>
          <Button fullWidth variant="default" leftSection={<IconPlus size={16} />} onClick={create}>
            <Trans>New dashboard</Trans>
          </Button>
        </ResponsiveOverlay>
      </>
    );
  }

  return (
    <h1 className={styles.title}>
      <Menu opened={opened} onChange={picker.set} position="bottom-start" width={260}>
        <Menu.Target>
          <button type="button" className={styles.titleButton}>
            {title}
            <IconChevronDown size={16} stroke={1.6} aria-hidden="true" />
          </button>
        </Menu.Target>
        <Menu.Dropdown>
          {choices.map(({ id, name }) => (
            <Menu.Item
              key={id}
              onClick={() => select(id)}
              aria-current={id === model.selected?.id || undefined}
              rightSection={id === model.selected?.id && <IconCheck size={16} />}>
              {name}
            </Menu.Item>
          ))}
          {!!choices.length && <Menu.Divider />}
          <Menu.Item leftSection={<IconPlus size={16} />} onClick={create}>
            <Trans>New dashboard</Trans>
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </h1>
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
