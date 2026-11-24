import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, Menu, Tabs } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArchive, IconChevronDown, IconDots, IconListDetails, IconMinus, IconPlus } from '@tabler/icons-react';
import { useState } from 'react';
import { PortfolioSelector } from '~/features/portfolios';
import { usePositions } from '~/features/positions';
import { PositionInspector } from '~/features/positions/components/positions-view/position-inspector';
import { PositionsView } from '~/features/positions/components/positions-view/positions-view';
import type { Position } from '~/features/positions/hooks/use-positions-view';
import { useBulkTradeModalStore, useTransactionModalStore } from '~/features/trades';
import { TradesList } from '~/features/trades/components/trades-list/trades-list';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { ArchivePortfolioModal } from '../components/archive-portfolio/archive-portfolio';
import { EarningsOverview } from '../components/earnings-overview/earnings-overview';
import { getPortfolioBalances } from '../components/portfolio-overview/portfolio-balances';
import { PortfolioAllocation, PortfolioOverview } from '../components/portfolio-overview/portfolio-overview';
import { usePortfolioName } from '../hooks/use-portfolio-name';
import styles from './portfolio-page.module.css';

export function PortfolioPage({ portfolioId }: { portfolioId: string }) {
  return <PortfolioContent key={portfolioId} portfolioId={portfolioId} />;
}

function PortfolioContent({ portfolioId }: { portfolioId: string }) {
  const { t } = useLingui();
  const model = usePortfolioView(portfolioId);
  const isMobile = useIsMobile();
  const heading = (
    <div className={styles.titleGroup}>
      <h1>
        <PortfolioSelector className={styles.titleButton}>
          {model.name || t`Portfolio`}
          <IconChevronDown className={styles.mobileChevron} size={16} aria-hidden="true" />
        </PortfolioSelector>
      </h1>
      <PortfolioMenu portfolioId={portfolioId} isMobile={isMobile} />
    </div>
  );
  const actions = <PortfolioActions portfolioId={portfolioId} />;
  const inspector = <PositionInspector position={model.selectedPosition} onClose={model.closePosition} weight={model.selectedWeight} />;

  if (isMobile) {
    return (
      <div className={styles.page}>
        {heading}
        <PortfolioOverview query={model.positions} balances={model.balances} />
        {actions}
        <PortfolioSections model={model} isMobile portfolioId={portfolioId} />
        {inspector}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.heading}>
        {heading}
        {actions}
      </div>
      <PortfolioOverview
        query={model.positions}
        balances={model.balances}
        onOpenPosition={model.openPosition}
        highlight={model.highlight}
      />
      <PortfolioSections model={model} isMobile={false} portfolioId={portfolioId} />
      {inspector}
    </div>
  );
}

function usePortfolioView(portfolioId: string) {
  const name = usePortfolioName(portfolioId);
  const positions = usePositions(Number(portfolioId));
  const [selectedId, setSelectedId] = useState<string>();
  const [hoveredId, setHoveredId] = useState<string>();
  const [focusedId, setFocusedId] = useState<string>();
  const [tab, setTab] = useState<string | null>('positions');
  const openPositions = positions.data?.filter(({ quantity }) => quantity > 0);
  const balances = getPortfolioBalances(openPositions ?? []);
  const selectedPosition = openPositions?.find(({ id }) => id === selectedId);
  const allocations = new Map(
    balances.flatMap(({ segments }) => segments.map(({ position, weight, colorIndex }) => [position.id, { weight, colorIndex }] as const))
  );

  return {
    name,
    positions,
    openPositionCount: openPositions?.length,
    balances,
    allocations,
    highlight: { id: hoveredId ?? focusedId, onHover: setHoveredId, onFocus: setFocusedId },
    tab,
    setTab,
    selectedPosition,
    selectedWeight: selectedPosition ? allocations.get(selectedPosition.id)?.weight : undefined,
    openPosition: ({ id }: Position) => setSelectedId(id),
    closePosition: () => setSelectedId(undefined)
  };
}

function PortfolioSections({
  model,
  isMobile,
  portfolioId
}: {
  model: ReturnType<typeof usePortfolioView>;
  isMobile: boolean;
  portfolioId: string;
}) {
  const { t } = useLingui();
  const positions = (
    <PositionsView
      portfolioId={Number(portfolioId)}
      onOpenPosition={model.openPosition}
      allocations={model.allocations}
      highlight={model.highlight}
    />
  );
  const earnings = <EarningsOverview portfolioId={Number(portfolioId)} />;
  const trades = <TradesList portfolioId={portfolioId} headingId={isMobile ? undefined : 'portfolio-trades-title'} />;

  if (isMobile) {
    return (
      <Tabs
        value={model.tab}
        onChange={model.setTab}
        variant="unstyled"
        classNames={{ list: styles.tabs, tab: styles.tab, panel: styles.panel }}>
        <Tabs.List aria-label={t`Portfolio sections`} grow>
          <Tabs.Tab value="positions">
            <Trans>Positions</Trans>
          </Tabs.Tab>
          <Tabs.Tab value="earnings">
            <Trans>Earnings</Trans>
          </Tabs.Tab>
          <Tabs.Tab value="trades">
            <Trans>Trades</Trans>
          </Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="positions">
          <div className={styles.mobilePositions}>
            <PortfolioAllocation balances={model.balances} onOpenPosition={model.openPosition} highlight={model.highlight} />
            {positions}
          </div>
        </Tabs.Panel>
        <Tabs.Panel value="earnings">{earnings}</Tabs.Panel>
        <Tabs.Panel value="trades">{trades}</Tabs.Panel>
      </Tabs>
    );
  }

  return (
    <div className={styles.sections}>
      <section aria-labelledby="portfolio-positions-title">
        <div className={styles.sectionHeading}>
          <h2 id="portfolio-positions-title">
            <Trans>Active positions</Trans>
          </h2>
          {model.openPositionCount != null && (
            <span>
              <Plural value={model.openPositionCount} one="# asset" other="# assets" />
            </span>
          )}
          {!!model.openPositionCount && (
            <span className={styles.positionHint}>
              <Trans>Click a row for details</Trans>
            </span>
          )}
        </div>
        {positions}
      </section>
      <section aria-labelledby="portfolio-earnings-title">
        <h2 id="portfolio-earnings-title">
          <Trans>Earnings</Trans>
        </h2>
        {earnings}
      </section>
      <section aria-labelledby="portfolio-trades-title">{trades}</section>
    </div>
  );
}

function PortfolioMenu({ portfolioId, isMobile }: { portfolioId: string; isMobile: boolean }) {
  const { t } = useLingui();
  const [opened, archive] = useDisclosure();
  const [mobileMenuOpen, mobileMenu] = useDisclosure();
  const label = t`Portfolio actions`;
  const modal = <ArchivePortfolioModal opened={opened} close={archive.close} portfolioId={portfolioId} />;

  if (isMobile) {
    return (
      <>
        <ActionIcon variant="subtle" className={styles.menuButton} aria-label={label} aria-haspopup="dialog" onClick={mobileMenu.open}>
          <IconDots size={19} aria-hidden="true" />
        </ActionIcon>
        <ResponsiveOverlay opened={mobileMenuOpen} onClose={mobileMenu.close} title={label}>
          <Button
            fullWidth
            color="loss"
            variant="subtle"
            leftSection={<IconArchive size={16} />}
            onClick={() => {
              mobileMenu.close();
              archive.open();
            }}>
            <Trans>Archive portfolio</Trans>
          </Button>
        </ResponsiveOverlay>
        {modal}
      </>
    );
  }

  return (
    <>
      <Menu position="bottom-start">
        <Menu.Target>
          <ActionIcon variant="subtle" className={styles.menuButton} aria-label={label}>
            <IconDots size={19} aria-hidden="true" />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item color="loss" leftSection={<IconArchive size={16} />} onClick={archive.open}>
            <Trans>Archive portfolio</Trans>
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      {modal}
    </>
  );
}

function PortfolioActions({ portfolioId }: { portfolioId: string }) {
  const { t } = useLingui();
  const openTrade = useTransactionModalStore((state) => state.open);
  const openBulk = useBulkTradeModalStore((state) => state.open);

  return (
    <fieldset className={styles.actions} aria-label={t`Portfolio trade actions`}>
      <Button leftSection={<IconPlus size={16} />} onClick={() => openTrade('Buy', undefined, portfolioId)}>
        <Trans comment="Button or filter for a purchase of an asset">Buy</Trans>
      </Button>
      <Button leftSection={<IconMinus size={16} />} variant="default" onClick={() => openTrade('Sell', undefined, portfolioId)}>
        <Trans comment="Button or filter for a sale of an asset">Sell</Trans>
      </Button>
      <Button leftSection={<IconListDetails size={16} />} variant="default" onClick={() => openBulk(Number(portfolioId))}>
        <Trans comment="Short action to open the form for entering many trades">Bulk</Trans>
      </Button>
    </fieldset>
  );
}
