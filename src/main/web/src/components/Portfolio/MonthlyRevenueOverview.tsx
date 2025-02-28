import { Box, Card, type CardProps, Center, Loader, ScrollArea, Text, Tooltip, rem } from '@mantine/core';
import { IconCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import React from 'react';
import { queries } from '~/api';
import type { MonthlyRevenueOverview as MonthlyRevenueOverviewType } from '~/api/queries/types';
import { constants } from '~/lib/constants';
import { format } from '~/lib/format';
import { ErrorView } from '../ErrorView';

export function MonthlyRevenueOverview() {
  const { data, status } = useQuery(queries.analytics.monthlyRevenueOverview());

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
    <MonthlyRevenueOverviewCard p={0} pt="xs">
      <ScrollArea scrollbars="x" type="auto" offsetScrollbars>
        <div
          style={{
            paddingInline: '--var(mantine-spacing-sm)',
            display: 'grid',
            gridTemplateColumns: 'auto repeat(12, minmax(35px, 1fr))',
            gap: 'var(--mantine-spacing-xs)',
            alignItems: 'center',
            padding: 'var(--mantine-spacing-xs)',
            minWidth: 'max-content'
          }}>
          {/* Year rows */}
          {fullData.map((s) => (
            <React.Fragment key={s.year}>
              <Text
                ta="center"
                size="sm"
                fw={600}
                style={{
                  whiteSpace: 'nowrap',
                  paddingRight: 8,
                  letterSpacing: '0.075rem'
                }}>
                {s.year}
              </Text>
              {s.data.map((m) => (
                <Tooltip key={`${m.month}-${s.year}`} label={m.profit === 0 ? 'No data found' : format.toCurrency(m.profit, false)}>
                  <Box
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      height: 35,
                      padding: '4px',
                      backgroundColor:
                        m.profit === 0
                          ? 'var(--mantine-color-gray-8)'
                          : m.profit > 0
                            ? 'var(--mantine-color-teal-9)'
                            : 'var(--mantine-color-red-9)',
                      borderRadius: 'var(--mantine-radius-sm)',
                      cursor: 'pointer',
                      minWidth: 50,
                      flexShrink: 0
                    }}>
                    {m.profit === 0 ? (
                      <IconCircle size="14px" />
                    ) : (
                      <Text
                        size="xs"
                        style={{
                          overflow: 'hidden',
                          lineHeight: 1.2,
                          textOverflow: 'ellipsis'
                        }}>
                        {`${m.profit > 0 ? '+' : ''}${format.toHumanizedNumber(m.profit)}`}
                      </Text>
                    )}
                  </Box>
                </Tooltip>
              ))}
            </React.Fragment>
          ))}
          {/* Month names row */}
          <div /> {/* Empty cell for year column */}
          {monthShortNames.map((name) => (
            <Text
              key={name}
              size="xs"
              ta="center"
              fw={600}
              style={{
                whiteSpace: 'nowrap',
                lineHeight: 1.2,
                letterSpacing: '0.05rem'
              }}>
              {name}
            </Text>
          ))}
        </div>
      </ScrollArea>
    </MonthlyRevenueOverviewCard>
  );
}
