import { ActionIcon, Container, Group, Stack, Text, Title } from '@mantine/core';
import { IconPencilCog, IconPointFilled } from '@tabler/icons-react';
import { TransactionHistory } from '~/features/portfolios/components/transaction-history';
import { usePortfolioName } from '~/features/portfolios/hooks/use-portfolio-name';
import { usePositions } from '~/features/positions';
import { ArchivePortfolioButton } from '../components/archive-portfolio/archive-portfolio';
import { EarningsOverview } from '../components/earnings-overview/earnings-overview';
import { PortfolioEmptyState } from '../components/portfolio-empty-state/portfolio-empty-state';
import { PortfolioOverviewCard } from '../components/portfolio-overview/portfolio-overview-card';
import { PositionsTable } from '../components/positions-table/positions-table';

export function PortfolioPage({ portfolioId }: { portfolioId: string }) {
  const portfolioName = usePortfolioName(portfolioId);
  const { data: positions, status } = usePositions(Number(portfolioId));

  const isEmpty = status === 'success' && positions?.length === 0;

  return (
    <Container strategy="grid" size="lg" p={{ base: 'md', sm: 'xl' }} w="100%">
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

        {isEmpty ? (
          <PortfolioEmptyState />
        ) : (
          <>
            <PortfolioOverviewCard />
            <PositionsTable key={portfolioId} />
          </>
        )}

        <EarningsOverview portfolioId={Number(portfolioId)} />
        <TransactionHistory />
      </Stack>
    </Container>
  );
}
