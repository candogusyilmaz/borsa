import {
  ActionIcon,
  Badge,
  Button,
  Card,
  Container,
  Divider,
  Group,
  MultiSelect,
  NumberInput,
  Popover,
  ScrollArea,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconFilter, IconX } from '@tabler/icons-react';
import { createFileRoute } from '@tanstack/react-router';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type SortingState,
  useReactTable
} from '@tanstack/react-table';
import { useState } from 'react';

// Placeholder types (minimal subset) and data instead of API queries
type BasicPortfolioView = { id: string; name: string };
type TradeHistoryTrade = {
  date: string;
  createdAt: string;
  holdingId: string;
  symbol: string;
  price: number;
  quantity: number;
  total: number;
  latest: boolean;
} & ({ type: 'BUY' } | { type: 'SELL'; profit: number; returnPercentage: number; performanceCategory: string });

import { format } from '~/lib/format';

export const Route = createFileRoute('/_authenticated/_member/trades')({
  component: RouteComponent
});

function RouteComponent() {
  return (
    <Container
      strategy="grid"
      size="lg"
      style={{ margin: 'var(--mantine-spacing-lg) var(--mantine-spacing-xl) var(--mantine-spacing-lg) var(--mantine-spacing-lg)' }}>
      <Stack gap="lg">
        <Group>
          <Title>Trades</Title>
        </Group>
        <TradesTable />
      </Stack>
    </Container>
  );
}

// Placeholder data -----------------------------------------------------------
const PLACEHOLDER_PORTFOLIOS: BasicPortfolioView[] = [
  { id: '1', name: 'Core Holdings' },
  { id: '2', name: 'Growth Focus' },
  { id: '3', name: 'Dividends' }
];

// Helpers to build strongly typed trades
function buy(base: Omit<TradeHistoryTrade, 'type'> & { portfolioId: string; portfolioName: string }): TradeHistoryTrade & {
  portfolioId: string;
  portfolioName: string;
} {
  return { ...base, type: 'BUY' } as const;
}
function sell(
  base: Omit<TradeHistoryTrade, 'type'> & { portfolioId: string; portfolioName: string },
  extra: { profit: number; returnPercentage: number; performanceCategory: string }
): TradeHistoryTrade & { portfolioId: string; portfolioName: string } {
  return { ...base, type: 'SELL', ...extra } as const;
}

const now = Date.now();
const PLACEHOLDER_TRADES_RAW: Array<TradeHistoryTrade & { portfolioId: string; portfolioName: string }> = [
  buy({
    portfolioId: '1',
    portfolioName: 'Core Holdings',
    date: new Date(now - 1000 * 60 * 60 * 5).toISOString(),
    createdAt: new Date(now - 1000 * 60 * 60 * 5).toISOString(),
    holdingId: 'h1',
    symbol: 'AAPL',
    price: 210.35,
    quantity: 10,
    total: 2103.5,
    latest: true
  }),
  sell(
    {
      portfolioId: '1',
      portfolioName: 'Core Holdings',
      date: new Date(now - 1000 * 60 * 60 * 24 * 1).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 1).toISOString(),
      holdingId: 'h2',
      symbol: 'MSFT',
      price: 398.1,
      quantity: 5,
      total: 1990.5,
      latest: true
    },
    { profit: 320.25, returnPercentage: 18.9, performanceCategory: 'WIN' }
  ),
  sell(
    {
      portfolioId: '2',
      portfolioName: 'Growth Focus',
      date: new Date(now - 1000 * 60 * 30).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 30).toISOString(),
      holdingId: 'h3',
      symbol: 'NVDA',
      price: 950.42,
      quantity: 2,
      total: 1900.84,
      latest: true
    },
    { profit: -120.5, returnPercentage: -6.7, performanceCategory: 'LOSS' }
  ),
  buy({
    portfolioId: '2',
    portfolioName: 'Growth Focus',
    date: new Date(now - 1000 * 60 * 60 * 12).toISOString(),
    createdAt: new Date(now - 1000 * 60 * 60 * 12).toISOString(),
    holdingId: 'h4',
    symbol: 'TSLA',
    price: 255.6,
    quantity: 12,
    total: 3067.2,
    latest: true
  }),
  buy({
    portfolioId: '3',
    portfolioName: 'Dividends',
    date: new Date(now - 1000 * 60 * 60 * 48).toISOString(),
    createdAt: new Date(now - 1000 * 60 * 60 * 48).toISOString(),
    holdingId: 'h5',
    symbol: 'KO',
    price: 62.15,
    quantity: 50,
    total: 3107.5,
    latest: true
  }),
  sell(
    {
      portfolioId: '3',
      portfolioName: 'Dividends',
      date: new Date(now - 1000 * 60 * 60 * 72).toISOString(),
      createdAt: new Date(now - 1000 * 60 * 60 * 72).toISOString(),
      holdingId: 'h6',
      symbol: 'JNJ',
      price: 152.9,
      quantity: 15,
      total: 2293.5,
      latest: true
    },
    { profit: 180.75, returnPercentage: 8.6, performanceCategory: 'WIN' }
  )
].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

const PLACEHOLDER_TRADES = PLACEHOLDER_TRADES_RAW;

function TradesTable() {
  return <TradesTableInner trades={PLACEHOLDER_TRADES} portfolios={PLACEHOLDER_PORTFOLIOS} />;
}

type ExtendedTrade = TradeHistoryTrade & { portfolioId: string; portfolioName: string };

function TradesTableInner({ trades, portfolios }: { trades: Array<ExtendedTrade>; portfolios: BasicPortfolioView[] }) {
  'use no memo';
  const columnHelper = createColumnHelper<ExtendedTrade>();
  const [sorting, setSorting] = useState<SortingState>([{ id: 'date', desc: true }]);
  const [selectedPortfolios, setSelectedPortfolios] = useState<string[]>([]);
  const [typeFilter, setTypeFilter] = useState<string[]>([]);
  const [symbolSearch, setSymbolSearch] = useState('');
  const [dateRange, setDateRange] = useState<[Date | null, Date | null]>([null, null]);
  const [minProfit, setMinProfit] = useState<number | ''>('');
  const [maxProfit, setMaxProfit] = useState<number | ''>('');

  const filteredTrades = trades.filter((t) => {
    if (selectedPortfolios.length && !selectedPortfolios.includes(t.portfolioId)) return false;
    if (typeFilter.length && !typeFilter.includes(t.type)) return false;
    if (symbolSearch && !t.symbol.toLowerCase().includes(symbolSearch.toLowerCase())) return false;
    if (dateRange[0] && new Date(t.date) < dateRange[0]) return false;
    if (dateRange[1] && new Date(t.date) > dateRange[1]) return false;
    if (minProfit !== '' && t.type === 'SELL') {
      const profit = (t as Extract<ExtendedTrade, { type: 'SELL' }>).profit;
      if (profit < minProfit) return false;
    }
    if (maxProfit !== '' && t.type === 'SELL') {
      const profit = (t as Extract<ExtendedTrade, { type: 'SELL' }>).profit;
      if (profit > maxProfit) return false;
    }
    return true;
  });

  const totalProfit = filteredTrades.reduce((acc, t) => {
    if (t.type === 'SELL') {
      return acc + (t as Extract<ExtendedTrade, { type: 'SELL' }>).profit;
    }
    return acc;
  }, 0);

  const columns = [
    columnHelper.accessor('portfolioName', {
      header: 'Portfolio',
      cell: ({ getValue }) => (
        <Badge variant="outline" size="sm" radius="sm" color="blue">
          {getValue()}
        </Badge>
      )
    }),
    columnHelper.accessor('symbol', {
      header: 'Symbol',
      cell: ({ getValue }) => (
        <Text inherit fw={600}>
          {getValue()}
        </Text>
      )
    }),
    columnHelper.accessor('type', {
      header: 'Type',
      cell: ({ getValue }) => {
        const val = getValue();
        return (
          <Badge
            size="sm"
            radius="sm"
            variant="light"
            color={val === 'BUY' ? 'teal' : 'red'}
            styles={{ label: { letterSpacing: '0.05rem' } }}>
            {val}
          </Badge>
        );
      }
    }),
    columnHelper.accessor('price', {
      header: 'Price',
      cell: ({ getValue }) => format.toCurrency(getValue())
    }),
    columnHelper.accessor('quantity', {
      header: 'Quantity',
      cell: ({ getValue }) => format.toHumanizedNumber(getValue())
    }),
    columnHelper.display({
      id: 'profit',
      header: 'Profit',
      cell: ({ row }) => {
        if (row.original.type === 'BUY') {
          return (
            <Text inherit c="dimmed">
              -
            </Text>
          );
        }
        const profit = (row.original as Extract<ExtendedTrade, { type: 'SELL' }>).profit;
        return (
          <Text inherit c={profit >= 0 ? 'teal' : 'red'} fw={500}>
            {format.toCurrency(profit)}
          </Text>
        );
      }
    }),
    columnHelper.accessor('date', {
      header: 'Action Date',
      cell: ({ getValue }) => format.toShortDateTime(new Date(getValue()))
    })
  ];

  const table = useReactTable({
    data: filteredTrades,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    state: { sorting }
  });

  return (
    <Stack>
      <FilterBar
        portfolios={portfolios}
        selectedPortfolios={selectedPortfolios}
        setSelectedPortfolios={setSelectedPortfolios}
        typeFilter={typeFilter}
        setTypeFilter={setTypeFilter}
        symbolSearch={symbolSearch}
        setSymbolSearch={setSymbolSearch}
        dateRange={dateRange}
        setDateRange={setDateRange}
        minProfit={minProfit}
        setMinProfit={setMinProfit}
        maxProfit={maxProfit}
        setMaxProfit={setMaxProfit}
        onClear={() => {
          setSelectedPortfolios([]);
          setTypeFilter([]);
          setSymbolSearch('');
          setDateRange([null, null]);
          setMinProfit('');
          setMaxProfit('');
        }}
        totalProfit={totalProfit}
        totalTrades={trades.length}
        filteredTrades={filteredTrades.length}
      />
      <Card shadow="sm" radius="md" p={0} withBorder>
        <ScrollArea h="100%" scrollbars="x" type="auto">
          <Table
            verticalSpacing="md"
            horizontalSpacing="sm"
            withRowBorders={false}
            highlightOnHover
            styles={{
              table: { fontSize: 'var(--mantine-font-size-xs)', whiteSpace: 'nowrap' },
              thead: { borderBottom: '1px solid var(--mantine-color-dark-5)' },
              th: { textTransform: 'uppercase', letterSpacing: '0.05rem', cursor: 'pointer' },
              td: { letterSpacing: '0.03rem' }
            }}>
            <Table.Thead>
              {table.getHeaderGroups().map((headerGroup) => (
                <Table.Tr key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <Table.Th
                      key={header.id}
                      onClick={() => header.column.getCanSort() && header.column.toggleSorting()}
                      ta={['symbol', 'portfolioName'].includes(header.id) ? 'left' : 'right'}>
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
                    <Table.Td key={cell.id} ta={['symbol', 'portfolioName'].includes(cell.column.id) ? 'left' : 'right'}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Card>
      <Text c="dimmed" size="xs">
        * Trade dates are shown in your local timezone.
      </Text>
    </Stack>
  );
}

// FilterBar component implementing chip-like filter popovers
type FilterBarProps = {
  portfolios: BasicPortfolioView[];
  selectedPortfolios: string[];
  setSelectedPortfolios: (v: string[]) => void;
  typeFilter: string[];
  setTypeFilter: (v: string[]) => void;
  symbolSearch: string;
  setSymbolSearch: (v: string) => void;
  dateRange: [Date | null, Date | null];
  setDateRange: (v: [Date | null, Date | null]) => void;
  minProfit: number | '';
  setMinProfit: (v: number | '') => void;
  maxProfit: number | '';
  setMaxProfit: (v: number | '') => void;
  onClear: () => void;
  totalProfit: number;
  totalTrades: number;
  filteredTrades: number;
};

function FilterBar({
  portfolios,
  selectedPortfolios,
  setSelectedPortfolios,
  typeFilter,
  setTypeFilter,
  symbolSearch,
  setSymbolSearch,
  dateRange,
  setDateRange,
  minProfit,
  setMinProfit,
  maxProfit,
  setMaxProfit,
  onClear,
  totalProfit,
  totalTrades,
  filteredTrades
}: FilterBarProps) {
  const [pfOpen, setPfOpen] = useState(false);
  const [typeOpen, setTypeOpen] = useState(false);
  const [dateOpen, setDateOpen] = useState(false);
  const [profitOpen, setProfitOpen] = useState(false);
  const [symbolOpen, setSymbolOpen] = useState(false);

  return (
    <Card shadow="sm" radius="md" p="xs" withBorder>
      <Group gap={8} wrap="wrap" align="center">
        <FilterChip
          label="Portfolios"
          opened={pfOpen}
          onChange={setPfOpen}
          active={selectedPortfolios.length > 0}
          count={selectedPortfolios.length}>
          <MultiSelect
            data={portfolios.map((p) => ({ value: p.id, label: p.name }))}
            value={selectedPortfolios}
            onChange={setSelectedPortfolios}
            searchable
            clearable
            placeholder="Select portfolios"
            size="xs"
          />
          <Group gap={4} justify="space-between">
            <Button size="compact-xs" variant="subtle" onClick={() => setSelectedPortfolios(portfolios.map((p) => p.id))}>
              Select All
            </Button>
            <Button size="compact-xs" variant="subtle" color="gray" onClick={() => setSelectedPortfolios([])}>
              Clear
            </Button>
          </Group>
        </FilterChip>
        <FilterChip label="Type" opened={typeOpen} onChange={setTypeOpen} active={typeFilter.length > 0} count={typeFilter.length}>
          <MultiSelect
            data={['BUY', 'SELL'].map((t) => ({ value: t, label: t }))}
            value={typeFilter}
            onChange={setTypeFilter}
            clearable
            size="xs"
            placeholder="Select type"
          />
          <Group justify="flex-end">
            <Button size="compact-xs" variant="subtle" color="gray" onClick={() => setTypeFilter([])}>
              Clear
            </Button>
          </Group>
        </FilterChip>
        <FilterChip label="Date" opened={dateOpen} onChange={setDateOpen} active={!!dateRange[0] || !!dateRange[1]}>
          <DatePickerInput
            type="range"
            value={dateRange}
            onChange={(val) => {
              if (!val) {
                setDateRange([null, null]);
                return;
              }
              const [start, end] = val;
              const normalize = (v: unknown): Date | null => {
                if (!v) return null;
                if (v instanceof Date) return v;
                if (typeof v === 'string' || typeof v === 'number') return new Date(v);
                return null;
              };
              setDateRange([normalize(start), normalize(end)]);
            }}
            size="xs"
            clearable
          />
          <Group justify="flex-end">
            <Button size="compact-xs" variant="subtle" color="gray" onClick={() => setDateRange([null, null])}>
              Reset
            </Button>
          </Group>
        </FilterChip>
        <FilterChip label="Profit" opened={profitOpen} onChange={setProfitOpen} active={minProfit !== '' || maxProfit !== ''}>
          <Group gap="xs" grow>
            <NumberInput
              label="Min"
              value={minProfit}
              onChange={(value) => {
                if (value === '' || value === null) setMinProfit('');
                else if (typeof value === 'number') setMinProfit(value);
              }}
              size="xs"
              allowNegative
            />
            <NumberInput
              label="Max"
              value={maxProfit}
              onChange={(value) => {
                if (value === '' || value === null) setMaxProfit('');
                else if (typeof value === 'number') setMaxProfit(value);
              }}
              size="xs"
              allowNegative
            />
          </Group>
          <Group justify="flex-end">
            <Button
              size="compact-xs"
              variant="subtle"
              color="gray"
              onClick={() => {
                setMinProfit('');
                setMaxProfit('');
              }}>
              Reset
            </Button>
          </Group>
        </FilterChip>
        <FilterChip label="Symbol" opened={symbolOpen} onChange={setSymbolOpen} active={!!symbolSearch}>
          <TextInput placeholder="Search symbol" value={symbolSearch} onChange={(e) => setSymbolSearch(e.currentTarget.value)} size="xs" />
          <Group justify="flex-end">
            <Button size="compact-xs" variant="subtle" color="gray" onClick={() => setSymbolSearch('')}>
              Clear
            </Button>
          </Group>
        </FilterChip>
        <Divider orientation="vertical" />
        <Group gap={8} wrap="nowrap">
          <Text size="xs" c="dimmed">
            {filteredTrades} / {totalTrades} trades
          </Text>
          <Badge size="xs" color={totalProfit >= 0 ? 'teal' : 'red'} variant="light">
            {format.toCurrency(totalProfit)}
          </Badge>
        </Group>
        <ActionIcon size="sm" variant="subtle" aria-label="Clear filters" onClick={onClear} ml="auto" color="gray">
          <IconX size={14} />
        </ActionIcon>
      </Group>
    </Card>
  );
}

// Standalone chip popover component
function FilterChip({
  label,
  opened,
  onChange,
  active,
  count,
  children
}: {
  label: string;
  opened: boolean;
  onChange: (v: boolean) => void;
  active?: boolean;
  count?: number;
  children: React.ReactNode;
}) {
  return (
    <Popover opened={opened} onChange={onChange} width={320} position="bottom-start" shadow="md" withinPortal>
      <Popover.Target>
        <Button
          size="xs"
          variant={active ? 'light' : 'subtle'}
          leftSection={<IconFilter size={14} />}
          onClick={() => onChange(!opened)}
          styles={{ label: { fontWeight: 500 } }}>
          <Group gap={6} wrap="nowrap">
            <Text size="xs">{label}</Text>
            {typeof count === 'number' && count > 0 && (
              <Badge size="xs" variant="filled" color="blue" radius="sm">
                {count}
              </Badge>
            )}
          </Group>
        </Button>
      </Popover.Target>
      <Popover.Dropdown p="sm">
        <Stack gap="xs">{children}</Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
