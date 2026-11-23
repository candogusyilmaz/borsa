import { ActionIcon, Button, Group, Menu, Tabs } from '@mantine/core';
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
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { ArchivePortfolioModal } from '../components/archive-portfolio/archive-portfolio';
import { EarningsOverview } from '../components/earnings-overview/earnings-overview';
import { PortfolioOverview } from '../components/portfolio-overview/portfolio-overview';
import { usePortfolioName } from '../hooks/use-portfolio-name';
import styles from './portfolio-page.module.css';

export function PortfolioPage({ portfolioId }: { portfolioId: string }) {
  return <PortfolioContent key={portfolioId} portfolioId={portfolioId} />;
}

function PortfolioContent({ portfolioId }: { portfolioId: string }) {
  const model = usePortfolioView(portfolioId);
  const isMobile = useIsMobile();

  return (
    <div className={styles.page}>
      <div className={styles.heading}>
        <h1>
          <PortfolioSelector className={styles.titleButton}>
            {model.name || 'Portfolio'}
            <IconChevronDown className={styles.mobileChevron} size={18} aria-hidden="true" />
          </PortfolioSelector>
        </h1>
        <PortfolioMenu portfolioId={portfolioId} />
      </div>
      <PortfolioOverview query={model.positions} onOpenPosition={model.openPosition} />
      <PortfolioActions portfolioId={portfolioId} />
      <PortfolioSections model={model} isMobile={isMobile} portfolioId={portfolioId} />
      <PositionInspector position={model.selectedPosition} onClose={model.closePosition} />
    </div>
  );
}

function usePortfolioView(portfolioId: string) {
  const name = usePortfolioName(portfolioId);
  const positions = usePositions(Number(portfolioId));
  const [selectedId, setSelectedId] = useState<string>();
  const [tab, setTab] = useState<string | null>('positions');
  return {
    name,
    positions,
    tab,
    setTab,
    selectedPosition: positions.data?.find(({ id }) => id === selectedId),
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
  const positions = <PositionsView portfolioId={Number(portfolioId)} onOpenPosition={model.openPosition} />;
  const earnings = <EarningsOverview portfolioId={Number(portfolioId)} />;
  const trades = <TradesList portfolioId={portfolioId} />;

  if (isMobile) {
    return (
      <Tabs
        value={model.tab}
        onChange={model.setTab}
        color="var(--app-weight-fill)"
        classNames={{ list: styles.tabs, tab: styles.tab, panel: styles.panel }}>
        <Tabs.List aria-label="Portfolio sections" grow>
          <Tabs.Tab value="positions">Positions</Tabs.Tab>
          <Tabs.Tab value="earnings">Earnings</Tabs.Tab>
          <Tabs.Tab value="trades">Trades</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="positions">{positions}</Tabs.Panel>
        <Tabs.Panel value="earnings">{earnings}</Tabs.Panel>
        <Tabs.Panel value="trades">{trades}</Tabs.Panel>
      </Tabs>
    );
  }

  return (
    <div className={styles.sections}>
      <section aria-labelledby="portfolio-positions-title">
        <h2 id="portfolio-positions-title">Active positions</h2>
        {positions}
      </section>
      <section aria-labelledby="portfolio-earnings-title">
        <h2 id="portfolio-earnings-title">Earnings</h2>
        {earnings}
      </section>
      <section aria-labelledby="portfolio-trades-title">
        <h2 id="portfolio-trades-title">Trades</h2>
        {trades}
      </section>
    </div>
  );
}

function PortfolioMenu({ portfolioId }: { portfolioId: string }) {
  const [opened, archive] = useDisclosure();

  return (
    <>
      <Menu position="bottom-end">
        <Menu.Target>
          <ActionIcon variant="default" aria-label="Portfolio actions">
            <IconDots size={19} />
          </ActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Item color="loss" leftSection={<IconArchive size={16} />} onClick={archive.open}>
            Archive portfolio
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      <ArchivePortfolioModal opened={opened} close={archive.close} portfolioId={portfolioId} />
    </>
  );
}

function PortfolioActions({ portfolioId }: { portfolioId: string }) {
  const openTrade = useTransactionModalStore((state) => state.open);
  const openBulk = useBulkTradeModalStore((state) => state.open);

  return (
    <Group gap="xs" aria-label="Portfolio trade actions">
      <Button leftSection={<IconPlus size={16} />} onClick={() => openTrade('Buy', undefined, portfolioId)}>
        Buy
      </Button>
      <Button leftSection={<IconMinus size={16} />} variant="default" onClick={() => openTrade('Sell', undefined, portfolioId)}>
        Sell
      </Button>
      <Button leftSection={<IconListDetails size={16} />} variant="subtle" onClick={() => openBulk(Number(portfolioId))}>
        Bulk
      </Button>
    </Group>
  );
}
