import { Card, type CardProps, Center, Group, ScrollArea, SimpleGrid, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { queries } from '~/api';
import type { PortfolioInfo } from '~/api/queries/types';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { BuyStockModal } from '~/components/Stocks/BuyStockModal';
import { SellStockModal } from '~/components/Stocks/SellStockModal';
import { format } from '~/lib/format';

const UPCOMING_FEATURE = true;

export function HoldingsCard() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = useQuery(queries.portfolio.fetchPortfolio({ portfolioId: Number(portfolioId) }));

  if (UPCOMING_FEATURE) {
    return (
      <Card
        shadow="sm"
        padding="lg"
        radius="md"
        withBorder
        w="100%"
        style={{
          borderColor: 'var(--mantine-color-orange-5)',
          whiteSpace: 'nowrap'
        }}>
        <Center h="100%" mih={100}>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            This feature is coming soon
          </Text>
        </Center>
      </Card>
    );
  }

  if (status === 'pending') {
    return (
      <HoldingsContainer>
        <LoadingView />
      </HoldingsContainer>
    );
  }

  if (status === 'error') {
    return (
      <HoldingsContainer style={{ borderColor: 'var(--mantine-color-red-5)' }}>
        <ErrorView />
      </HoldingsContainer>
    );
  }

  if (data.stocks.length === 0) {
    return (
      <HoldingsContainer>
        <Center h="100%" mih={100}>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            You currently don't own any holdings
          </Text>
        </Center>
      </HoldingsContainer>
    );
  }

  return <Inner data={data} />;
}

function HoldingsContainer({ children, ...props }: CardProps) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder flex={1} {...props}>
      <Title order={3}>Holdings</Title>
      {children}
    </Card>
  );
}

function Inner({ data }: { data: PortfolioInfo }) {
  return (
    <HoldingsContainer>
      <ScrollArea mt="md">
        <SimpleGrid cols={{ xs: 1, md: 2 }} mah={450}>
          {data.stocks.map((s) => (
            <Card withBorder shadow="0" key={`hhh-${s.symbol}`}>
              <Group justify="space-between" align="center">
                <Text fw={500}>{s.symbol}</Text>
                <Text fw={600} size="sm">
                  {format.toCurrency(s.currentPrice * s.quantity)}
                </Text>
              </Group>
              <Group justify="space-between" align="center">
                <Text c="dimmed" size="sm">
                  {format.toHumanizedNumber(s.quantity)} shares
                </Text>
                <Text size="xs" c={s.profitPercentage === 0 ? 'dimmed' : s.profitPercentage > 0 ? 'teal' : 'red'}>
                  {format.toCurrency(s.profit)}
                </Text>
              </Group>
              <Group mt="sm" justify="space-between" align="center" gap="lg">
                <BuyStockModal
                  stockId={s.id}
                  buttonProps={{
                    flex: 1,
                    variant: 'filled'
                  }}
                />
                <SellStockModal
                  stockId={s.id}
                  buttonProps={{
                    flex: 1,
                    variant: 'filled'
                  }}
                />
              </Group>
            </Card>
          ))}
        </SimpleGrid>
      </ScrollArea>
    </HoldingsContainer>
  );
}
