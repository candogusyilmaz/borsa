import { Container, Flex, Space, Stack, Title } from '@mantine/core';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { queries } from '~/api';
import { BalanceCard } from '~/components/Portfolio/BalanceCard';
import { HoldingsCard } from '~/components/Portfolio/HoldingsCard';
import { HoldingsTable } from '~/components/Portfolio/HoldingsTable';
import { MonthlyRevenueOverview } from '~/components/Portfolio/MonthlyRevenueOverview';
import { TransactionHistory } from '~/components/Portfolio/TransactionHistory';

export const Route = createFileRoute('/_authenticated/_member/portfolios/$portfolioId')({
  component: RouteComponent,
  beforeLoad: async ({ context: { queryClient }, params }) => {
    try {
      await queryClient.fetchQuery(queries.portfolio.fetchPortfolio({ portfolioId: Number(params.portfolioId) }));
    } catch (_error) {
      throw redirect({ to: '/overview' });
    }
  }
});

function RouteComponent() {
  return (
    <Container
      strategy="grid"
      size="lg"
      style={{ margin: 'var(--mantine-spacing-lg) var(--mantine-spacing-xl) var(--mantine-spacing-lg) var(--mantine-spacing-lg)' }}>
      <Stack>
        <Title>Portfolio</Title>
        <Flex gap="md" direction={{ base: 'column', sm: 'row' }}>
          <BalanceCard />
          <HoldingsCard />
        </Flex>
        <Space />
        <HoldingsTable />
        <Space />
        <MonthlyRevenueOverview />
        <Space />
        <TransactionHistory />
      </Stack>
    </Container>
  );
}
