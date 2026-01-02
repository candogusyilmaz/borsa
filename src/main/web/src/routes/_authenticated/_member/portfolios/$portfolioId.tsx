import { ActionIcon, Container, Flex, Group, Stack, Text, Title } from '@mantine/core';
import { IconPencilCog, IconPointFilled } from '@tabler/icons-react';
import { createFileRoute } from '@tanstack/react-router';
import { BalanceCard } from '~/components/Portfolio/BalanceCard';
import { HoldingsCard } from '~/components/Portfolio/HoldingsCard';
import { MonthlyRevenueOverview } from '~/components/Portfolio/MonthlyRevenueOverview';
import { TransactionHistory } from '~/components/Portfolio/TransactionHistory';
import { usePortfolioName } from '~/hooks/use-portfolio-name';
import { ArchivePortfolioButton } from './-components/archive-portfolio/archive-portfolio';
import { PositionsTable } from './-components/positions-table/positions-table';

export const Route = createFileRoute('/_authenticated/_member/portfolios/$portfolioId')({
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; q?: string }
});

function RouteComponent() {
  const { portfolioId } = Route.useParams();
  const portfolioName = usePortfolioName(portfolioId);

  return (
    <Container
      strategy="grid"
      size="lg"
      style={{ margin: 'var(--mantine-spacing-lg) var(--mantine-spacing-xl) var(--mantine-spacing-lg) var(--mantine-spacing-lg)' }}>
      <Stack gap="xl">
        <Group justify="space-between" align="center">
          <Stack gap={2}>
            <Group gap={4}>
              <IconPointFilled size={32} style={{ height: '100%' }} />
              <Title fw={800}>{portfolioName}</Title>
            </Group>
            <Text c="dimmed" fw={700} fz={10} lts={2} tt="uppercase" ml={10}>
              Active Portfolio
            </Text>
          </Stack>

          <Group>
            <ActionIcon variant="subtle" c="dimmed">
              <IconPencilCog size={18} />
            </ActionIcon>
            <ArchivePortfolioButton portfolioId={portfolioId} />
          </Group>
        </Group>
        <Flex gap="md" direction={{ base: 'column', sm: 'row' }}>
          <BalanceCard />
          <HoldingsCard />
        </Flex>
        <PositionsTable key={portfolioId} />
        <MonthlyRevenueOverview />
        <TransactionHistory />
      </Stack>
    </Container>
  );
}
