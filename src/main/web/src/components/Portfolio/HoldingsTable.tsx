import { Button, Card, Group, ScrollArea, Stack, Table, Text, rem } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { createColumnHelper, flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table';
import { queries } from '~/api';
import type { Balance } from '~/api/queries/types';
import { format } from '~/lib/format';
import { ErrorView } from '../ErrorView';
import { LoadingView } from '../LoadingView';
import { useTransactionModalStore } from '../Transaction/TransactionModal';

export function HoldingsTable() {
  const { data, status } = useQuery(queries.member.balance());
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
          <Button ml="auto" size="xs" variant="default" onClick={() => openModal('Buy')} c="teal">
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

  return <Inner data={data} />;
}

function Inner({ data: { stocks, totalValue } }: { data: Balance }) {
  'use no memo';
  type Stock = (typeof stocks)[0];
  const columnHelper = createColumnHelper<Stock>();

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

  return (
    <Stack>
      <Group>
        <Text fw={700} size={rem(22)}>
          Holdings
        </Text>
        <BuySellButtonGroup />
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
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Card>
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
