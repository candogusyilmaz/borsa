import { Badge, Group, Loader, Stack, Table, Text } from '@mantine/core';
import { IconClock } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { type ColumnDef, flexRender, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table';
import { useMemo } from 'react';
import { queries } from '~/api';
import type { Transaction } from '~/api/queries/trades';
import { format } from '~/lib/format';
import classes from './trade-history.module.css';

export function TradeHistoryTable({ positionId, avgCost }: { positionId: string; avgCost: number }) {
  'use no memo';
  const { data: trades, status } = useQuery(queries.position.fetchActiveTrades(Number(positionId)));

  const columns = useMemo<ColumnDef<Transaction>[]>(
    () => [
      {
        accessorKey: 'actionDate',
        header: 'Date',
        cell: (info) => (
          <Text inherit c="gray.4">
            {format.toShortDate(new Date(info.getValue<string>()))}
          </Text>
        ),
        sortingFn: 'datetime'
      },
      {
        accessorKey: 'type',
        header: 'Type',
        cell: (info) => (
          <Badge
            fz={10}
            radius="sm"
            variant="light"
            w={50}
            bd={`1px solid ${info.getValue<string>() === 'BUY' ? 'var(--mantine-color-teal-8)' : 'var(--mantine-color-red-9)'}`}
            color={info.getValue<string>() === 'BUY' ? 'teal' : 'red'}>
            {info.getValue<string>()}
          </Badge>
        )
      },
      {
        accessorKey: 'price',
        header: () => 'Price',
        cell: (info) => <Text inherit>{format.currency(info.getValue<number>())}</Text>
      },
      {
        accessorKey: 'quantity',
        header: () => 'Trade Qty',
        cell: (info) => <Text inherit>{info.getValue<number>()}</Text>
      },
      {
        accessorKey: 'newQuantity',
        header: () => 'New Qty',
        cell: (info) => <Text inherit>{info.getValue<number>()}</Text>
      },
      {
        accessorKey: 'newTotal',
        header: () => 'New Total',
        cell: (info) => <Text inherit>{format.currency(info.getValue<number>())}</Text>
      }
    ],
    []
  );

  const table = useReactTable({
    data: trades || [],
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    initialState: {
      sorting: [{ id: 'actionDate', desc: false }]
    }
  });

  if (status === 'pending') {
    return (
      <div className={classes.expandedContent}>
        <Stack align="center" gap="xs" mt="md">
          <Loader size="sm" />
          <Text fz="12" fw={600} c="gray.5">
            Loading trade history...
          </Text>
        </Stack>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <Text fz="12" fw={600} c="red">
        Failed to load trade history.
      </Text>
    );
  }

  return (
    <div className={classes.expandedContent}>
      <Group justify="space-between" mb="md">
        <Stack gap="3" align="flex-start">
          <Text fw={600} fz="14">
            Position Breakdown: <span style={{ color: 'var(--mantine-color-blue-4)' }}>{trades?.[0]?.position.instrumentSymbol}</span>
          </Text>
          <Text fz={12} c="dimmed">
            Displaying trades that contribute to current open quantity.
          </Text>
        </Stack>

        <Group>
          <Stack
            gap={2}
            align="flex-start"
            style={{
              backgroundColor: 'var(--mantine-color-dark-6)',
              borderRadius: '4px',
              padding: '0.5rem 1rem',
              border: '1px solid var(--mantine-color-dark-5)'
            }}>
            <Text fz={10} fw={700} c="dimmed" tt="uppercase">
              Total Volume
            </Text>
            <Text fz={12} fw={600}>
              {trades?.length ?? 0} trade{trades?.length !== 1 ? 's' : ''}
            </Text>
          </Stack>
          <Stack
            gap={2}
            align="flex-start"
            style={{
              backgroundColor: 'var(--mantine-color-dark-6)',
              borderRadius: '4px',
              padding: '0.5rem 1rem',
              border: '1px solid var(--mantine-color-dark-5)'
            }}>
            <Text fz={10} fw={700} c="dimmed" tt="uppercase">
              Break Even
            </Text>
            <Text fz={12} fw={600}>
              {format.currency(avgCost, { currency: trades[0].position.currencyCode })}
            </Text>
          </Stack>
        </Group>
      </Group>
      <Stack gap={0} bg="dark.5" bdrs={'sm'} bd="1px solid var(--mantine-color-gray-8)">
        <Group
          py={12}
          px={18}
          justify="space-between"
          align="center"
          style={{
            borderBottom: '1px solid var(--mantine-color-gray-8)'
          }}>
          <Group gap={8} c="gray.5">
            <IconClock size={16} color="var(--mantine-color-blue-4)" />
            <Text fz={12} fw={600} tt="uppercase">
              Trade History
            </Text>
          </Group>
          <Badge radius="sm" variant="default" tt="revert" py="sm" bg="dark.4" bd="1px solid var(--mantine-color-dark-3)" lts="0.005em">
            <Text fz={11} c="gray.6" fw={500}>
              {trades.length} Active Record{trades.length !== 1 ? 's' : ''}
            </Text>
          </Badge>
        </Group>
        <Table unstyled className={classes.table} verticalSpacing="sm">
          <Table.Thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <Table.Tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <Table.Th className={classes.tableHeader} key={header.id}>
                    {flexRender(header.column.columnDef.header, header.getContext())}
                  </Table.Th>
                ))}
              </Table.Tr>
            ))}
          </Table.Thead>
          <Table.Tbody>
            {table.getRowModel().rows.map((row) => (
              <Table.Tr className={classes.tableRow} key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <Table.Td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Stack>
    </div>
  );
}
