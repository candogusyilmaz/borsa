import { Badge, Group, Pagination, Stack, Table, Text } from '@mantine/core';
import { IconChevronDown, IconChevronUp } from '@tabler/icons-react';
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
import React, { Fragment, useMemo } from 'react';
import { $api } from '~/api/openapi';
import { TableStateHandler } from '~/components/table/table-state-handler';
import { format } from '~/lib/format';
import type { ElementType } from '~/lib/types';
import { TradeHistoryTable } from '../trade-history/trade-history';
import classes from './positions-table.module.css';

export function PositionsTable() {
  'use no memo';
  const navigate = useNavigate();
  const { page, q } = useSearch({
    from: '/_authenticated/_member/positions'
  });
  const { data: positions, status } = $api.useQuery('get', '/api/positions');

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
        accessorFn: (row) => row.portfolio.name,
        id: 'portfolioName',
        header: 'Portfolio',
        cell: (info) => (
          <Badge tt="revert" fw={500} py="xs" px="sm" variant="default" radius="sm" bg="rgba(37, 41, 53, 0.3)" bd="1px solid #3c3c3dff">
            <Text inherit c="gray.4">
              {info.getValue<string>()}
            </Text>
          </Badge>
        )
      },
      {
        accessorKey: 'quantity',
        header: () => (
          <Text inherit ta="right">
            Quantity
          </Text>
        )
      },
      {
        accessorKey: 'avgCost',
        header: () => (
          <Text inherit ta="right">
            Avg Cost
          </Text>
        ),
        cell: (info) => format.currency(info.getValue<number>(), { currency: info.row.original.instrument.currency })
      },
      {
        id: 'currentPrice',
        accessorFn: (row) => row.instrument.last,
        header: () => (
          <Text inherit ta="right">
            Price
          </Text>
        ),
        cell: (info) => format.currency(info.getValue<number>(), { currency: info.row.original.instrument.currency })
      },
      {
        id: 'totalValue',
        header: () => (
          <Text inherit ta="right">
            Total Value
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.original.quantity;
          const price = info.row.original.instrument.last;
          return (
            <Text inherit ta="right" fw="600">
              {format.currency(quantity * price)}
            </Text>
          );
        }
      },
      {
        id: 'profitLoss',
        header: () => (
          <Text inherit ta="right">
            P/L
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.original.quantity;
          const price = info.row.original.instrument.last;
          const returnValue = quantity * (price - info.row.original.avgCost);
          const returnPercentage = (price - info.row.original.avgCost) / info.row.original.avgCost;

          return (
            <Stack gap={0}>
              <Text inherit ta="right" fw="600" c={returnValue > 0 ? 'teal' : 'red'}>
                {format.currency(returnValue, { currency: info.row.original.instrument.currency })}
              </Text>
              <Text inherit ta="right" fz="11" c={returnPercentage > 0 ? 'teal' : 'red'}>
                {format.toLocalePercentage(returnPercentage * 100)}
              </Text>
            </Stack>
          );
        }
      },
      {
        id: 'expander',
        header: '',
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
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
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
      pagination: {
        pageIndex: page ? Number(page) - 1 : 0,
        pageSize: 10
      },
      columnVisibility: {
        portfolioId: false
      },
      globalFilter: q
    }
  });

  return (
    <React.Fragment>
      <Table.ScrollContainer
        minWidth={900}
        className={classes.tableWrapper}
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
      <Group justify="flex-end">
        <Pagination
          value={page ? Number(page) : 1}
          onChange={(v) => navigate({ to: '.', search: (old) => ({ ...old, page: v }) })}
          total={table.getPageCount()}
        />
      </Group>
    </React.Fragment>
  );
}
