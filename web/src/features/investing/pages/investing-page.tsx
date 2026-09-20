import { Button, Tabs, Text } from '@mantine/core';
import { ChartLineUpIcon, ClockCounterClockwiseIcon, PlusIcon, TrendUpIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { PositionList } from '../components/position-list/position-list';
import { TradeOverlay } from '../components/record-trade/record-trade';
import { TradeHistoryList } from '../components/trade-history/trade-history-list';
import classes from './investing-page.module.css';

export function InvestingPage() {
  const [activeTab, setActiveTab] = useState<string | null>('positions');

  return (
    <div className={classes.container}>
      {/* 1. Header Bar */}
      <div className={classes.headerBar}>
        <div className={classes.titleArea}>
          <TrendUpIcon size={28} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <div>
            <Text fw={700} size="xl">
              Investing &amp; Positions
            </Text>
            <Text size="xs" c="dimmed">
              Deterministic position projections and funded brokerage trades
            </Text>
          </div>
        </div>

        <Button
          size="sm"
          variant="filled"
          color="brand"
          leftSection={<PlusIcon size={16} weight="bold" />}
          onClick={() => TradeOverlay.open({})}
          style={{ minHeight: 40 }}>
          Record Trade
        </Button>
      </div>

      {/* 2. Tabs: Open Positions & Trade History */}
      <Tabs value={activeTab} onChange={setActiveTab}>
        <Tabs.List className={classes.tabsList}>
          <Tabs.Tab value="positions" leftSection={<ChartLineUpIcon size={16} weight="bold" />}>
            Open Positions
          </Tabs.Tab>
          <Tabs.Tab value="trades" leftSection={<ClockCounterClockwiseIcon size={16} weight="bold" />}>
            Trade History
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="positions" className={classes.tabPanel}>
          <PositionList />
        </Tabs.Panel>

        <Tabs.Panel value="trades" className={classes.tabPanel}>
          <TradeHistoryList />
        </Tabs.Panel>
      </Tabs>
    </div>
  );
}
