import { Container, Group, Pagination, Stack, Table, Text, Title } from '@mantine/core';
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
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
import { Fragment, useMemo } from 'react';
import { queries } from '~/api';
import type { Transaction } from '~/api/queries/trades';
import { format } from '~/lib/format';
import classes from './trades.module.css';

export const Route = createFileRoute('/_authenticated/_member/trades')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(queries.trades.fetchAllTransactions()),
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number }
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Title c="white">Transactions</Title>
        <TransactionsTable />
      </Stack>
    </Container>
  );
}

function TransactionsTable() {
  'use no memo';
  const { data: transactions } = useSuspenseQuery(queries.trades.fetchAllTransactions());
  const { page } = Route.useSearch();
  const navigate = Route.useNavigate();

  const columns = useMemo<ColumnDef<Transaction>[]>(
    () => [
      {
        accessorKey: 'actionDate',
        header: 'Date',
        cell: (info) => <Text inherit>{format.toShortDate(new Date(info.getValue<string>()))}</Text>,
        sortingFn: 'datetime'
      },
      {
        accessorKey: 'type',
        header: 'Type',
        cell: (info) => <TransactionTableTypeChip type={info.getValue<Transaction['type']>()} profit={info.row.original.profit} />
      },
      {
        accessorFn: (row) => row.position.instrumentSymbol,
        id: 'instrumentSymbol',
        header: 'Symbol',
        cell: (info) => (
          <Text inherit fw="bold">
            {info.getValue<string>()}
          </Text>
        )
      },
      {
        accessorFn: (row) => row.position.portfolio.name,
        id: 'portfolioName',
        header: 'Portfolio',
        cell: (info) => (
          <Text inherit c="dimmed">
            {info.getValue<string>()}
          </Text>
        )
      },
      {
        accessorKey: 'quantity',
        header: () => (
          <Text inherit ta="right">
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
          <Text inherit ta="right">
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
          <Text inherit ta="right">
            Total Value
          </Text>
        ),
        cell: (info) => {
          const quantity = info.row.getValue<number>('quantity');
          const price = info.row.getValue<number>('price');
          return (
            <Text inherit ta="right" fw="600">
              {format.currency(quantity * price)}
            </Text>
          );
        }
      }
    ],
    []
  );

  const table = useReactTable({
    data: transactions,
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
      }
    }
  });

  return (
    <>
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
                  <Table.Th key={header.id} colSpan={header.colSpan}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </Table.Th>
                ))}
              </Table.Tr>
            ))}
          </Table.Thead>
          <Table.Tbody>
            {table.getRowModel().rows.map((row) => (
              <Fragment key={row.id}>
                <Table.Tr>
                  {row.getVisibleCells().map((cell) => (
                    <Table.Td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</Table.Td>
                  ))}
                </Table.Tr>
              </Fragment>
            ))}
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
    </>
  );
}

function TransactionTableTypeChip({ type, profit }: { type: Transaction['type']; profit: number | null }) {
  let color: string;

  switch (type) {
    case 'BUY':
      color = 'blue';
      break;
    case 'SELL':
      color = profit !== undefined ? (profit! >= 0 ? 'teal' : 'red') : 'yellow';
      break;
    default:
      color = 'gray';
  }

  return (
    <Text
      inherit
      c={color}
      fz="xs"
      fw="bold"
      style={{
        textTransform: 'uppercase',
        letterSpacing: 0.5
      }}>
      {type}
    </Text>
  );
}
