import { Box, Card, type CardProps, Center, Group, Loader, Stack, Text, Tooltip, rem } from '@mantine/core';
import { IconCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { queries } from '~/api';
import type { MonthlyRevenueOverview as MonthlyRevenueOverviewType } from '~/api/queries/types';
import { constants } from '~/lib/constants';
import { format } from '~/lib/format';
import { ErrorView } from '../ErrorView';

export function MonthlyRevenueOverview() {
  const { data, status } = useQuery(queries.trades.monthlyRevenueOverview());

  if (status === 'pending') {
    return (
      <MonthlyRevenueOverviewCard>
        <Center h={100}>
          <Loader />
        </Center>
      </MonthlyRevenueOverviewCard>
    );
  }

  if (status === 'error') {
    return (
      <MonthlyRevenueOverviewCard style={{ borderColor: 'var(--mantine-color-red-5)' }}>
        <ErrorView />
      </MonthlyRevenueOverviewCard>
    );
  }

  if (data.length === 0) {
    return (
      <MonthlyRevenueOverviewCard>
        <Text c="dimmed" size="xs" fw={600} ta="center">
          You haven't{' '}
          <Text span inherit c="red">
            sold
          </Text>{' '}
          any shares.
        </Text>
      </MonthlyRevenueOverviewCard>
    );
  }

  return <Inner data={data} />;
}

function MonthlyRevenueOverviewCard({ children, ...props }: CardProps) {
  return (
    <>
      <Text fw={700} size={rem(22)}>
        Monthly Revenue Overview
      </Text>
      <Card shadow="sm" radius="md" p="lg" withBorder {...props}>
        {children}
      </Card>
    </>
  );
}

function Inner({ data }: { data: MonthlyRevenueOverviewType }) {
  const monthShortNames = Array.from({ length: 12 }, (_, index) =>
    new Intl.DateTimeFormat(constants.locale(), { month: 'short' }).format(new Date(2000, index, 1))
  );

  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  const fullData = data.map((yearData) => {
    const filledMonths = months.map((month) => {
      const existing = yearData.data.find((m) => m.month === month);
      return existing || { month, profit: 0 };
    });

    return { year: yearData.year, data: filledMonths };
  });

  return (
    <MonthlyRevenueOverviewCard>
      <div style={{ overflowX: 'auto' }}>
        <Stack justify="center" h="100%" miw="max-content" gap="xs">
          {fullData.map((s) => (
            <Group key={`${s.year}`} justify="center" grow wrap="nowrap" preventGrowOverflow gap="xs">
              <Text ta="center">{s.year}</Text>
              {s.data.map((m) => (
                <Tooltip key={`${m.month}-${s.year}`} label={m.profit === 0 ? 'No data found' : format.toCurrency(m.profit)}>
                  <Box
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      height: 35,
                      padding: '10px',
                      backgroundColor:
                        m.profit === 0
                          ? 'var(--mantine-color-gray-8)'
                          : m.profit > 0
                            ? 'var(--mantine-color-teal-9)'
                            : 'var(--mantine-color-red-9)',
                      borderRadius: '5px',
                      cursor: 'pointer',
                      minWidth: '50px'
                    }}>
                    {m.profit === 0 ? (
                      <IconCircle size="18px" />
                    ) : (
                      <Text fz="xs" fw={400}>{`${m.profit > 0 ? '+' : ''}${format.toHumanizedCurrency(m.profit)}`}</Text>
                    )}
                  </Box>
                </Tooltip>
              ))}
            </Group>
          ))}
          <Group align="center" grow>
            <Box />
            {monthShortNames.map((name) => (
              <Text key={name} size="xs" style={{ minWidth: 50, textAlign: 'center' }}>
                {name}
              </Text>
            ))}
          </Group>
        </Stack>
      </div>
    </MonthlyRevenueOverviewCard>
  );
}
