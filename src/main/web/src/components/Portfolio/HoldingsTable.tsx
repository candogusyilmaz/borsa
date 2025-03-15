import { ActionIcon, Button, Card, Group, Popover, ScrollArea, Stack, Switch, Table, Text, rem } from '@mantine/core';
import { IconSettings } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { createColumnHelper, flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table';
import { useState } from 'react';
import { queries } from '~/api';
import type { Portfolio, PortfolioStock } from '~/api/queries/types';
import { format } from '~/lib/format';
import { ErrorView } from '../ErrorView';
import { LoadingView } from '../LoadingView';
import { useTransactionModalStore } from '../Transaction/TransactionModal';

export function HoldingsTable() {
  const [includeCommission, setIncludeCommission] = useState(false);
  const { data, status } = useQuery(queries.portfolio.fetchPortfolio(includeCommission));
  const openModal = useTransactionModalStore((state) => state.open);

  if (status === 'pending') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Holdings
        </Text>
        <Card shadow="sm" radius="md" p="lg" withBorder>
          <LoadingView />
        </Card>
      </Stack>
    );
  }

  if (status === 'error') {
    return (
      <Stack>
        <Text fw={700} size={rem(22)}>
          Holdings
        </Text>
        <Card shadow="sm" radius="md" p="lg" withBorder style={{ borderColor: 'var(--mantine-color-red-5)' }}>
          <ErrorView />
        </Card>
      </Stack>
    );
  }

  if (data.stocks.length === 0) {
    return (
      <Stack>
        <Group>
          <Text fw={700} size={rem(22)}>
            Holdings
          </Text>
          <Button size="xs" variant="default" onClick={() => openModal('Buy')} c="teal">
            Buy
          </Button>
        </Group>

        <Card shadow="sm" radius="md" withBorder>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            You don't have any holdings at the moment
          </Text>
        </Card>
      </Stack>
    );
  }

  return <Inner data={data} includeCommission={includeCommission} onToggleCommission={setIncludeCommission} />;
}

function Inner({
  data: { stocks, totalValue, totalCost, totalProfitPercentage },
  includeCommission,
  onToggleCommission
}: {
  data: Portfolio;
  includeCommission: boolean;
  onToggleCommission: (value: boolean) => void;
}) {
  'use no memo';
  const columnHelper = createColumnHelper<PortfolioStock>();

  const columns = [
    columnHelper.accessor('symbol', {
      header: 'Symbol',
      enableHiding: false,
      cell: ({ getValue }) => (
        <Text inherit fw={600}>
          {getValue()}
        </Text>
      )
    }),
    columnHelper.accessor('quantity', {
      header: 'Qty'
    }),
    columnHelper.accessor('currentPrice', {
      header: 'Price',
      cell: ({ getValue }) => format.toCurrency(getValue())
    }),
    columnHelper.accessor('averagePrice', {
      header: 'Avg. Cost',
      cell: ({ getValue }) => format.toCurrency(getValue())
    }),
    columnHelper.accessor('value', {
      header: 'Value',
      cell: ({ getValue }) => format.toCurrency(getValue())
    }),
    columnHelper.accessor('cost', {
      header: 'Cost',
      cell: ({ getValue }) => format.toCurrency(getValue())
    }),
    columnHelper.accessor('dailyProfit', {
      header: 'Daily Profit',
      cell: ({ getValue }) => (
        <Text inherit c={getValue() >= 0 ? 'teal' : 'red'}>
          {format.toCurrency(getValue())}
        </Text>
      )
    }),
    columnHelper.accessor('dailyChangePercent', {
      header: 'Daily %',
      cell: ({ getValue }) => (
        <Text inherit c={getValue() >= 0 ? 'teal' : 'red'}>
          {format.toLocalePercentage(getValue())}
        </Text>
      )
    }),
    columnHelper.accessor('profit', {
      header: 'Total Profit',
      cell: ({ getValue }) => (
        <Text inherit c={getValue() >= 0 ? 'teal' : 'red'}>
          {format.toCurrency(getValue())}
        </Text>
      )
    }),
    columnHelper.accessor('profitPercentage', {
      header: 'Total Profit %',
      cell: ({ getValue }) => (
        <Text inherit c={getValue() >= 0 ? 'teal' : 'red'}>
          {format.toLocalePercentage(getValue())}
        </Text>
      )
    }),
    columnHelper.display({
      id: 'portfolioPercent',
      header: 'Portfolio %',
      cell: ({ row }) => format.toLocalePercentage((row.original.value / totalValue) * 100)
    })
  ];

  const table = useReactTable({
    data: stocks,
    columns,
    getCoreRowModel: getCoreRowModel()
  });

  const totals = stocks.reduce(
    (acc, stock) => {
      acc.value += stock.value;
      acc.cost += stock.cost;
      acc.profit += stock.profit;
      acc.dailyProfit += stock.dailyProfit;
      return acc;
    },
    {
      value: 0,
      cost: 0,
      profit: 0,
      dailyProfit: 0
    }
  );

  const totalDailyChangePercent = totals.cost !== 0 ? (totals.dailyProfit / totals.cost) * 100 : 0;

  return (
    <Stack>
      <Group gap={4}>
        <Text fw={700} size={rem(22)}>
          Holdings
        </Text>
        <BuySellButtonGroup />
        <Popover width={200} position="bottom-end" withArrow shadow="md">
          <Popover.Target>
            <ActionIcon variant="transparent" color="gray">
              <IconSettings style={{ width: '75%' }} />
            </ActionIcon>
          </Popover.Target>
          <Popover.Dropdown>
            <Switch
              size="xs"
              color="green"
              label="Include commission"
              labelPosition="left"
              checked={includeCommission}
              onChange={(event) => onToggleCommission(event.target.checked)}
            />
          </Popover.Dropdown>
        </Popover>
      </Group>
      <Card shadow="sm" radius="md" p={0} withBorder>
        <ScrollArea h="100%" scrollbars="x" offsetScrollbars type="auto">
          <Table
            verticalSpacing="md"
            horizontalSpacing="sm"
            withRowBorders={false}
            highlightOnHover
            styles={{
              table: {
                fontSize: 'var(--mantine-font-size-xs)',
                whiteSpace: 'nowrap'
              },
              thead: {
                borderBottom: '1px solid var(--mantine-color-dark-5)'
              },
              th: {
                textTransform: 'uppercase',
                letterSpacing: '0.05rem'
              },
              td: {
                letterSpacing: '0.03rem'
              }
            }}>
            <Table.Thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <Table.Tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <Table.Th key={header.id} ta={header.id === 'symbol' ? 'left' : 'right'}>
                      {flexRender(header.column.columnDef.header, header.getContext())}
                    </Table.Th>
                  ))}
                </Table.Tr>
              ))}
            </Table.Thead>
            <Table.Tbody>
              {table.getRowModel().rows.map((row) => (
                <Table.Tr key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <Table.Td key={cell.id} ta={cell.column.id === 'symbol' ? 'left' : 'right'}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
              <Table.Tr
                style={{
                  borderTop: '1px solid var(--mantine-color-dark-5)'
                }}>
                <Table.Td ta="left" fw={700}>
                  TOTAL
                </Table.Td>
                <Table.Td />
                <Table.Td />
                <Table.Td />
                <Table.Td ta="right">{format.toCurrency(totalValue)}</Table.Td>
                <Table.Td ta="right">{format.toCurrency(totalCost)}</Table.Td>
                <Table.Td ta="right" c={totals.dailyProfit >= 0 ? 'teal' : 'red'}>
                  {format.toCurrency(totals.dailyProfit)}
                </Table.Td>
                <Table.Td ta="right" c={totalDailyChangePercent >= 0 ? 'teal' : 'red'}>
                  {format.toLocalePercentage(totalDailyChangePercent)}
                </Table.Td>
                <Table.Td ta="right" c={totals.profit >= 0 ? 'teal' : 'red'}>
                  {format.toCurrency(totals.profit)}
                </Table.Td>
                <Table.Td ta="right" c={totalProfitPercentage >= 0 ? 'teal' : 'red'}>
                  {format.toLocalePercentage(totalProfitPercentage)}
                </Table.Td>
                <Table.Td ta="right">{format.toLocalePercentage(100)}</Table.Td>
              </Table.Tr>
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Card>

      <Text c="dimmed" size="xs">
        * Last prices are calculated with a 15-minute delay and updated every 30 seconds
      </Text>
    </Stack>
  );
}

function BuySellButtonGroup() {
  const openModal = useTransactionModalStore((state) => state.open);

  return (
    <Button.Group ml="auto">
      <Button size="xs" variant="default" onClick={() => openModal('Buy')} c="teal">
        Buy
      </Button>
      <Button size="xs" variant="default" onClick={() => openModal('Sell')} c="red">
        Sell
      </Button>
    </Button.Group>
  );
}
