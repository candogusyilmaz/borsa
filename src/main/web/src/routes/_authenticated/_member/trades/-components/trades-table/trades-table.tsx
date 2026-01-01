import { Badge, Group, Pagination, Stack, Table, Text } from '@mantine/core';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable
} from '@tanstack/react-table';
import { useMemo } from 'react';
import { $api } from '~/api/openapi';
import { TableStateHandler } from '~/components/table/table-state-handler';
import { getColorByReturnPercentage } from '~/lib/common';
import { format } from '~/lib/format';
import type { ElementType } from '~/lib/types';
import { TradeHistoryFilter } from './trade-history-filter';
import classes from './trades-table.module.css';

export function TradesTable() {
  'use no memo';
  const { data: trades, status } = $api.useQuery('get', '/api/trades');
  const { page, q } = useSearch({ from: '/_authenticated/_member/trades' });
  const navigate = useNavigate();

  const columns = useMemo<ColumnDef<ElementType<typeof trades>>[]>(
    () => [
      {
        accessorFn: (row) => row.position.instrumentSymbol,
        id: 'instrumentSymbol',
        header: 'Symbol',
        cell: (info) => (
          <Group gap={8} preventGrowOverflow wrap="nowrap">
            <div className={classes.tickerIcon}>{info.row.original.position.instrumentSymbol.substring(0, 2)}</div>
            <Stack gap={0}>
              <Text inherit fw="bold" className={classes.tickerSymbol}>
                {info.row.original.position.instrumentSymbol}
              </Text>
              <Text
                inherit
                fz={10}
                style={{
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  width: 150
                }}>
                {info.row.original.position.instrumentName}
              </Text>
            </Stack>
          </Group>
        )
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
        accessorFn: (row) => row.position.portfolio.name,
        id: 'portfolioName',
        header: 'Portfolio',
        cell: (info) => (
          <Badge tt="revert" fw={500} py="xs" px="sm" variant="outline" radius="sm" color="violet">
            <Text inherit>{info.getValue<string>()}</Text>
          </Badge>
        )
      },
      {
        accessorKey: 'quantity',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Quantity
          </Text>
        ),
        cell: (info) => (
          <Text inherit ta="right">
            {info.getValue<number>()}
          </Text>
        )
      },
      {
        accessorKey: 'price',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Price
          </Text>
        ),
        cell: (info) => (
          <Text inherit ta="right">
            {format.currency(info.getValue<number>(), { currency: info.row.original.position.currencyCode })}
          </Text>
        )
      },
      {
        id: 'totalValue',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Total Value
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.getValue<number>('quantity');
          const price = info.row.getValue<number>('price');
          const returnValue = info.row.original.profit;

          return (
            <Stack gap={0}>
              <Text inherit ta="right" fw="600">
                {format.currency(quantity * price, { currency: info.row.original.position.currencyCode })}
              </Text>
              {returnValue && (
                <Text inherit ta="right" fz="11" c={getColorByReturnPercentage(returnValue)}>
                  {format.currency(returnValue, { currency: info.row.original.position.currencyCode })}
                </Text>
              )}
            </Stack>
          );
        }
      },

      {
        accessorKey: 'actionDate',
        header: () => (
          <Text inherit ta="right" c="dimmed">
            Date
          </Text>
        ),
        cell: (info) => <Text inherit>{format.toShortDate(new Date(info.getValue<string>()))}</Text>,
        sortingFn: 'datetime'
      }
    ],
    []
  );

  const table = useReactTable({
    data: trades ?? [],
    columns,
    getRowCanExpand: () => true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getExpandedRowModel: getExpandedRowModel(),
    initialState: {
      sorting: [
        {
          id: 'actionDate',
          desc: true
        }
      ]
    },
    state: {
      pagination: {
        pageIndex: page ? Number(page) - 1 : 0,
        pageSize: 10
      },
      globalFilter: q
    }
  });

  return (
    <>
      <TradeHistoryFilter />
      <Table.ScrollContainer
        className={classes.tableWrapper}
        minWidth={900}
        scrollAreaProps={{
          offsetScrollbars: false
        }}>
        <Table unstyled className={classes.table}>
          <thead>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <th className={classes.tableHeader} key={header.id} colSpan={header.colSpan}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id} className={classes.tableRow}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
                ))}
              </tr>
            ))}
            <TableStateHandler status={status} empty={trades?.length === 0} span={table.getVisibleFlatColumns().length} />
          </tbody>
        </Table>
      </Table.ScrollContainer>
      <Group justify="flex-end">
        <Pagination
          value={page ? Number(page) : 1}
          onChange={(v) => navigate({ to: '.', search: (old) => ({ ...old, page: v }) })}
          total={table.getPageCount()}
        />
      </Group>
    </>
  );
}
