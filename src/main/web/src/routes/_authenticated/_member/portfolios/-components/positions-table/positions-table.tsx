import { Button, Group, Stack, Table, Text } from '@mantine/core';
import { IconChevronDown, IconChevronUp } from '@tabler/icons-react';
import { useParams } from '@tanstack/react-router';
import { type ColumnDef, flexRender, getCoreRowModel, getExpandedRowModel, useReactTable } from '@tanstack/react-table';
import { Fragment, useMemo } from 'react';
import { $api } from '~/api/openapi';
import { useBulkTransactionModalStore } from '~/components/Transaction/BulkTransactionModal';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';
import { TableStateHandler } from '~/components/table/table-state-handler';
import { getColorByReturnPercentage, isDataStale } from '~/lib/common';
import { format } from '~/lib/format';
import type { ElementType } from '~/lib/types';
import { TradeHistoryTable } from '../trade-history/trade-history';
import classes from './positions-table.module.css';

export function PositionsTable() {
  const { portfolioId } = useParams({
    from: '/_authenticated/_member/portfolios/$portfolioId'
  });
  const { data: positions, status } = $api.useQuery('get', '/api/positions', { params: { query: { portfolioId: Number(portfolioId) } } });
  const openModal = useTransactionModalStore((state) => state.open);
  const openBulkTransactionModal = useBulkTransactionModalStore((s) => s.open);

  const columns = useMemo<ColumnDef<ElementType<typeof positions>>[]>(
    () => [
      {
        accessorFn: (row) => row.instrument.symbol,
        id: 'instrumentSymbol',
        header: 'Symbol',
        cell: (info) => (
          <Group gap={8} preventGrowOverflow wrap="nowrap">
            <div className={classes.tickerIcon}>{info.row.original.instrument.symbol.substring(0, 2)}</div>
            <Stack gap={0}>
              <Text inherit fw="bold" className={classes.tickerSymbol}>
                {info.row.original.instrument.symbol}
              </Text>
              <Text
                inherit
                fz={10}
                style={{
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  width: 100
                }}>
                {info.row.original.instrument.name}
              </Text>
            </Stack>
          </Group>
        )
      },
      {
        accessorFn: (row) => row.portfolio.id,
        id: 'portfolioId',
        enableHiding: true
      },

      {
        accessorKey: 'quantity',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Quantity
          </Text>
        )
      },
      {
        accessorKey: 'avgCost',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Avg Cost
          </Text>
        ),
        cell: (info) => format.currency(info.getValue<number>(), { currency: info.row.original.currencyCode })
      },
      {
        id: 'currentPrice',
        accessorFn: (row) => row.instrument.last,
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Price
          </Text>
        ),
        cell: (info) => format.currency(info.getValue<number>(), { currency: info.row.original.currencyCode })
      },
      {
        id: 'totalValue',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Total Value
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.original.quantity;
          const price = info.row.original.instrument.last;
          return (
            <Text inherit ta="right" fw="600">
              {price ? format.currency(quantity * price) : 'N/A'}
            </Text>
          );
        }
      },
      {
        id: 'dailyProfitLoss',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Daily P/L
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.original.quantity;
          const dailyChange = info.row.original.instrument.dailyChange;
          const lastUpdatedTimestamp = info.row.original.instrument.updatedAt;

          if (dailyChange === undefined || !info.row.original.instrument.last || isDataStale(lastUpdatedTimestamp))
            return (
              <Text inherit ta="right" fw="600" c="dimmed">
                N/A
              </Text>
            );

          const dailyReturnValue = quantity * dailyChange;
          const dailyReturnPercentage = (dailyChange * 100) / (info.row.original.instrument.last - dailyChange);
          const color = getColorByReturnPercentage(dailyReturnPercentage);

          return (
            <Stack gap={0}>
              <Text inherit ta="right" fw="600" c={color}>
                {format.currency(dailyReturnValue, { currency: info.row.original.currencyCode })}
              </Text>
              <Text inherit ta="right" fz="11" c={color}>
                {format.toLocalePercentage(dailyReturnPercentage)}
              </Text>
            </Stack>
          );
        }
      },
      {
        id: 'profitLoss',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            P/L
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.original.quantity;
          const price = info.row.original.instrument.last;

          if (price === undefined)
            return (
              <Text inherit ta="right" fw="600" c="dimmed">
                N/A
              </Text>
            );

          const returnValue = quantity * (price - info.row.original.avgCost);
          const returnPercentage = ((price - info.row.original.avgCost) * 100) / info.row.original.avgCost;
          const color = getColorByReturnPercentage(returnPercentage, {
            lastUpdatedTimestamp: info.row.original.instrument.updatedAt
          });

          return (
            <Stack gap={0}>
              <Text inherit ta="right" fw="600" c={color}>
                {format.currency(returnValue, { currency: info.row.original.currencyCode })}
              </Text>
              <Text inherit ta="right" fz="11" c={color}>
                {format.toLocalePercentage(returnPercentage)}
              </Text>
            </Stack>
          );
        }
      },
      {
        id: 'expander',
        header: '',
        enableSorting: false,
        cell: (info) => {
          const expanded = info.row.getIsExpanded();
          return (
            <div style={{ width: '100%', display: 'flex', justifyContent: 'center', alignItems: 'center', color: 'gray' }}>
              {expanded ? <IconChevronUp className={classes.expander} /> : <IconChevronDown className={classes.expander} />}
            </div>
          );
        }
      }
    ],
    []
  );

  const table = useReactTable({
    data: positions ?? [],
    columns,
    getRowCanExpand: () => true,
    getCoreRowModel: getCoreRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    initialState: {
      sorting: [
        {
          id: 'instrumentSymbol',
          desc: false
        }
      ]
    },
    state: {
      columnVisibility: {
        portfolioId: false
      }
    }
  });

  return (
    <div className={classes.tableWrapper}>
      <Group p="md" justify="space-between">
        <Text fw="bold" size="sm">
          Active Positions
        </Text>
        <Button.Group>
          <Button
            styles={{
              root: {
                borderColor: 'var(--mantine-color-gray-8)'
              }
            }}
            size="xs"
            variant="default"
            onClick={() => openBulkTransactionModal(portfolioId!)}>
            Bulk
          </Button>
          <Button
            styles={{
              root: {
                borderColor: 'var(--mantine-color-gray-8)',
                borderLeftWidth: 0,
                borderRightWidth: 0
              }
            }}
            size="xs"
            variant="default"
            onClick={() => openModal('Buy')}
            c="teal">
            Buy
          </Button>
          <Button
            styles={{
              root: {
                borderColor: 'var(--mantine-color-gray-8)'
              }
            }}
            size="xs"
            variant="default"
            onClick={() => openModal('Sell')}
            c="red">
            Sell
          </Button>
        </Button.Group>
      </Group>
      <Table.ScrollContainer
        minWidth={900}
        scrollAreaProps={{
          offsetScrollbars: false
        }}>
        <Table unstyled className={classes.table}>
          <Table.Thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <Table.Tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <Table.Th key={header.id} className={classes.tableHeader} colSpan={header.colSpan}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </Table.Th>
                ))}
              </Table.Tr>
            ))}
          </Table.Thead>
          <Table.Tbody>
            {table.getRowModel().rows.map((row) => (
              <Fragment key={row.id}>
                <Table.Tr
                  onClick={() => {
                    const wasExpanded = row.getIsExpanded();
                    table.resetExpanded();
                    if (!wasExpanded) {
                      row.toggleExpanded(true);
                    }
                  }}
                  className={classes.tableRow}
                  data-expanded={row.getIsExpanded() ? 'true' : 'false'}>
                  {row.getVisibleCells().map((cell) => (
                    <Table.Td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</Table.Td>
                  ))}
                </Table.Tr>
                {row.getIsExpanded() && (
                  <tr className={classes.expandedRowContainer}>
                    <td colSpan={row.getVisibleCells().length}>
                      <TradeHistoryTable positionId={row.original.id} avgCost={row.original.avgCost} />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
            <TableStateHandler
              status={status}
              empty={positions?.length === 0 || table.getRowCount() === 0}
              span={table.getVisibleFlatColumns().length}
            />
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
    </div>
  );
}
