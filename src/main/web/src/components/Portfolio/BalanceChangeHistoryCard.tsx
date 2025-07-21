import { AreaChart } from '@mantine/charts';
import { Card, Center, rem, Stack, Text } from '@mantine/core';
import type { BalanceHistory } from '~/api/queries/types';
import { format } from '~/lib/format';

interface StockEntry {
  date: string;
  stock: string;
  balance: number;
}

interface LatestBalances {
  [stock: string]: StockEntry;
}

interface ChartData {
  date: string;

  [stock: string]: number | string; // Dynamic keys for stock balances
}

function mapData(data: StockEntry[]) {
  const groupedByDay: { [date: string]: StockEntry[] } = data.reduce(
    (acc, entry) => {
      const dateKey = entry.date.split('T')[0]; // Extract the date part (YYYY-MM-DD)
      if (!acc[dateKey]) {
        acc[dateKey] = [];
      }
      acc[dateKey].push(entry);
      return acc;
    },
    {} as { [date: string]: StockEntry[] }
  );

  // Step 2: Sort the dates in ascending order
  const sortedDates = Object.keys(groupedByDay).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());

  // Step 3: Calculate the latest balances for each day, considering only entries on or before that day
  const dailyTotals: ChartData[] = sortedDates.map((date) => {
    const currentDate = new Date(date);

    // Filter all entries on or before the current date
    const entriesUpToDate = data.filter((entry) => new Date(entry.date.split('T')[0]) <= currentDate);

    // Find the latest entry for each stock up to the current date
    const latestEntries: LatestBalances = entriesUpToDate.reduce((acc, entry) => {
      const entryDate = new Date(entry.date.split('T')[0]);
      if (!acc[entry.stock] || entryDate > new Date(acc[entry.stock].date.split('T')[0])) {
        acc[entry.stock] = entry;
      }
      return acc;
    }, {} as LatestBalances);

    // Sum the balances of the latest entries for this day
    const totalBalance = Object.values(latestEntries).reduce((sum, entry) => sum + entry.balance, 0);

    // Create the chart data object
    const chartData: ChartData = {
      date: format.toShortDate(currentDate),
      Balance: totalBalance
    };

    for (const [stock, entry] of Object.entries(latestEntries)) {
      chartData[stock] = entry.balance;
    }

    return chartData;
  });

  return dailyTotals;
}

export function BalanceChangeHistoryCard({ data }: { data: BalanceHistory }) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder flex={1}>
      <Stack h="100%">
        <Text size={rem(24)} fw={600}>
          Change History
        </Text>
        <Center h="100%">
          <AreaChart
            mt="auto"
            ml={20}
            h="100%"
            w={'calc(100% - 40px)'}
            withLegend
            data={mapData(data)}
            dataKey="date"
            series={[{ name: 'Balance', color: 'teal' }]}
            connectNulls
            tooltipAnimationDuration={200}
            valueFormatter={(value) => format.toCurrency(value)}
          />
        </Center>
      </Stack>
    </Card>
  );
}
