import { Badge, Group, Loader, Stack, Table, Text } from '@mantine/core';
import { IconClock } from '@tabler/icons-react';
import { type ColumnDef, flexRender, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table';
import { useMemo } from 'react';
import { $api } from '~/api/openapi';
import { format } from '~/lib/format';
import type { ElementType } from '~/lib/types';
import classes from './trade-history.module.css';

export function TradeHistoryTable({ positionId, avgCost }: { positionId: string; avgCost: number }) {
  'use no memo';
  const { data: trades, status } = $api.useQuery('get', '/api/positions/{positionId}/active-trades', {
    params: {
      path: {
        positionId: Number(positionId)
      }
    }
  });

  const columns = useMemo<ColumnDef<ElementType<typeof trades>>[]>(
    () => [
      {
        accessorKey: 'actionDate',
        header: 'Date',
        cell: (info) => (
          <Text inherit c="dimmed" fz={11} fw={500}>
            {format.toShortDate(new Date(info.getValue<string>()))}
          </Text>
        ),
        sortingFn: 'datetime'
      },
      {
        accessorKey: 'type',
        header: 'Type',
        cell: (info) => {
          const type = info.getValue<string>();
          const isBuy = type === 'BUY';
          return (
            <Badge
              fz={9}
              radius="sm"
              variant="light"
              w={54}
              h={20}
              tt="uppercase"
              fw={800}
              lts="0.05em"
              color={isBuy ? 'profit' : 'loss'}
              styles={{
                root: {
                  border: isBuy ? '1px solid var(--cv-profit-dim)' : '1px solid var(--cv-loss-dim)',
                  backgroundColor: isBuy ? 'var(--cv-profit-muted)' : 'var(--cv-loss-muted)',
                  color: isBuy ? 'var(--cv-profit)' : 'var(--cv-loss)'
                }
              }}>
              {type}
            </Badge>
          );
        }
      },
      {
        accessorKey: 'price',
        header: () => 'Price',
        cell: (info) => (
          <Text inherit fz={12} fw={600} ff="var(--mantine-font-family-monospace)">
            {format.currency(info.getValue<number>())}
          </Text>
        )
      },
      {
        accessorKey: 'quantity',
        header: () => 'Trade Qty',
        cell: (info) => (
          <Text inherit fz={12} fw={500} ff="var(--mantine-font-family-monospace)">
            {info.getValue<number>()}
          </Text>
        )
      },
      {
        accessorKey: 'newQuantity',
        header: () => 'New Qty',
        cell: (info) => (
          <Text inherit fz={12} fw={600} ff="var(--mantine-font-family-monospace)">
            {info.getValue<number>()}
          </Text>
        )
      },
      {
        accessorKey: 'newTotal',
        header: () => 'New Total',
        cell: (info) => (
          <Text inherit fz={12} fw={600} c="var(--cv-brand-500)" ff="var(--mantine-font-family-monospace)">
            {format.currency(info.getValue<number>())}
          </Text>
        )
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
      <Group justify="space-between" mb="lg">
        <Stack gap="2" align="flex-start">
          <Text fw={800} fz="16" tt="uppercase" lts="0.02em">
            Position Breakdown: <span style={{ color: 'var(--cv-brand-500)' }}>{trades?.[0]?.position.instrumentSymbol}</span>
          </Text>
          <Text fz={11} c="dimmed" fw={500}>
            Displaying active trades contributing to the current open quantity.
          </Text>
        </Stack>

        <Group gap="sm">
          <Stack
            gap={2}
            align="flex-start"
            style={{
              backgroundColor: 'var(--cv-card-bg)',
              backdropFilter: 'var(--cv-card-blur)',
              borderRadius: '8px',
              padding: '0.6rem 1.25rem',
              border: '1px solid var(--cv-border)'
            }}>
            <Text fz={9} fw={800} c="dimmed" tt="uppercase" lts="0.1em">
              Total Volume
            </Text>
            <Text fz={14} fw={700} ff="var(--mantine-font-family-monospace)">
              {trades?.length ?? 0} <span style={{ fontSize: '10px', fontWeight: 500, opacity: 0.6 }}>Trades</span>
            </Text>
          </Stack>
          <Stack
            gap={2}
            align="flex-start"
            style={{
              backgroundColor: 'var(--cv-card-bg)',
              backdropFilter: 'var(--cv-card-blur)',
              borderRadius: '8px',
              padding: '0.6rem 1.25rem',
              border: '1px solid var(--cv-border)'
            }}>
            <Text fz={9} fw={800} c="dimmed" tt="uppercase" lts="0.1em">
              Break Even
            </Text>
            <Text fz={14} fw={700} ff="var(--mantine-font-family-monospace)" style={{ color: 'var(--cv-brand-500)' }}>
              {format.currency(avgCost, { currency: trades?.[0]?.position.currencyCode })}
            </Text>
          </Stack>
        </Group>
      </Group>

      <Stack gap={0} bdrs={'md'} bd="1px solid var(--cv-border)" bg="var(--cv-card-bg)" style={{ overflow: 'hidden' }}>
        <Group
          py={12}
          px={18}
          justify="space-between"
          align="center"
          style={{
            borderBottom: '1px solid var(--cv-border)',
            backgroundColor: 'var(--cv-row-hover)'
          }}>
          <Group gap={8}>
            <IconClock size={16} stroke={2.5} color="var(--cv-brand-500)" />
            <Text fz={10} fw={800} tt="uppercase" lts="0.15em" c="dimmed">
              Trade History Records
            </Text>
          </Group>
          <Badge
            radius="sm"
            variant="outline"
            tt="none"
            h={24}
            bg="var(--cv-card-bg)"
            bd="1px solid var(--cv-border)"
            styles={{ label: { color: 'var(--cv-text-muted)', fontWeight: 600, fontSize: '10px' } }}>
            {trades?.length} Active {trades?.length !== 1 ? 'Entries' : 'Entry'}
          </Badge>
        </Group>
        <Table className={classes.table} verticalSpacing="md" withRowBorders={false}>
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
