import { Button, Container, Group, Paper, Stack, Text, TextInput, ThemeIcon, Title, useMatches } from '@mantine/core';
import { useDebouncedState } from '@mantine/hooks';

import { IconArrowNarrowDownDashed, IconArrowNarrowUpDashed, IconSearch } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useWindowVirtualizer } from '@tanstack/react-virtual';
import { useEffect, useRef, useState } from 'react';

import { queries } from '~/api';

import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';
import { format } from '~/lib/format';

export const Route = createFileRoute('/_authenticated/_member/stocks')({
  component: RouteComponent,
  beforeLoad: ({ context }) => {
    context.queryClient.ensureQueryData(queries.stocks.fetchAll('BIST'));
  }
});

function RouteComponent() {
  const { data: stocks } = useQuery(queries.stocks.fetchAll('BIST'));
  const { data: balance } = useQuery(queries.portfolio.fetchPortfolio(false));
  const [filteredSymbols, setFilteredSymbols] = useState(stocks?.symbols);
  const nameSize = useMatches({
    base: 75,
    xs: 100,
    sm: 175,
    md: 225,
    lg: 275
  });
  const [q, setQ] = useDebouncedState('', 300);

  useEffect(() => {
    setFilteredSymbols(stocks?.symbols.filter((s) => s.name.toLowerCase().includes(q) || s.symbol.toLowerCase().includes(q.toLowerCase())));
  }, [stocks, q]);

  const listRef = useRef<HTMLDivElement | null>(null);

  const virtualizer = useWindowVirtualizer({
    count: filteredSymbols?.length ?? 0,
    estimateSize: () => 80,
    overscan: 5,
    scrollMargin: listRef.current?.offsetTop ?? 0
  });

  const openModal = useTransactionModalStore((state) => state.open);

  return (
    <Container size="lg" my="lg">
      <Title mb="xl">Stocks</Title>
      <TextInput
        size="md"
        mb="md"
        leftSection={<IconSearch size="50%" />}
        placeholder="Search by name or symbol e.g. FROTO"
        onChange={(event) => setQ(event.currentTarget.value)}
      />
      {filteredSymbols && (
        <div ref={listRef}>
          <div
            style={{
              height: `${virtualizer.getTotalSize()}px`,
              width: '100%',
              position: 'relative'
            }}>
            {virtualizer.getVirtualItems().map((item) => (
              <div
                key={item.key}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: `${item.size}px`,
                  transform: `translateY(${item.start - virtualizer.options.scrollMargin}px)`
                }}>
                <Paper
                  px="sm"
                  py="xs"
                  withBorder
                  bg="dark.6"
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr minmax(auto, 100px) minmax(auto,100px) minmax(auto,100px) auto',
                    columnGap: 'var(--mantine-spacing-md)'
                  }}>
                  <Group gap="xs" align="center">
                    <ThemeIcon variant="transparent" size="md">
                      {filteredSymbols[item.index].dailyChange >= 0 ? (
                        <IconArrowNarrowUpDashed size="100%" color="teal" />
                      ) : (
                        <IconArrowNarrowDownDashed size="100%" color="red" />
                      )}
                    </ThemeIcon>
                    <Stack gap={0}>
                      <Text fw={600}>{filteredSymbols[item.index].symbol}</Text>
                      <Text c="gray.4" truncate="end" maw={nameSize}>
                        {filteredSymbols[item.index].name}
                      </Text>
                    </Stack>
                  </Group>
                  <Group align="center" justify="flex-end">
                    <Text fz="sm" fw={300}>
                      {format.toCurrency(filteredSymbols[item.index].last)}
                    </Text>
                  </Group>
                  <Group gap="xs" justify="flex-end">
                    <Text c={filteredSymbols[item.index].dailyChange >= 0 ? 'teal' : 'red'} fz="sm" fw={300}>
                      {format.toCurrency(filteredSymbols[item.index].dailyChange)}
                    </Text>
                  </Group>
                  <Group align="center" justify="flex-end">
                    <Text c={filteredSymbols[item.index].dailyChangePercent >= 0 ? 'teal' : 'red'} fw={300} fz="sm">
                      {format.toLocalePercentage(filteredSymbols[item.index].dailyChangePercent)}
                    </Text>
                  </Group>
                  <Stack gap={0} justify="center" align="flex-end">
                    <Button
                      key={`buy-${filteredSymbols[item.index].id}`}
                      variant="subtle"
                      size="compact-xs"
                      color="teal.7"
                      fw={400}
                      fz="xs"
                      onClick={() => {
                        openModal('Buy', filteredSymbols[item.index].id);
                      }}>
                      Buy
                    </Button>
                    {balance?.stocks?.find((s) => s.symbol === filteredSymbols[item.index].symbol) && (
                      <Button
                        key={`sell-${filteredSymbols[item.index].id}`}
                        variant="subtle"
                        size="compact-xs"
                        color="red.7"
                        fw={400}
                        fz="xs"
                        onClick={() => openModal('Sell', filteredSymbols[item.index].id)}>
                        Sell
                      </Button>
                    )}
                  </Stack>
                </Paper>
              </div>
            ))}
          </div>
        </div>
      )}
    </Container>
  );
}
