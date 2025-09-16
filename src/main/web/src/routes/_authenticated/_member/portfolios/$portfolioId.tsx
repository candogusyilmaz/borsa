import { Button, Container, Flex, Group, Menu, Space, Stack, Title } from '@mantine/core';
import { IconArchive, IconPencilCode, IconSettings, IconWorld } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { queries } from '~/api';
import { useArchivePortfolioModalStore } from '~/components/Portfolio/ArchivePortfolioModal';
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
      throw redirect({ to: '/dashboard' });
    }
  }
});

function RouteComponent() {
  const { data: currencies } = useQuery(queries.currency.getAllCurrencies());
  const { open: openArchiveModal } = useArchivePortfolioModalStore();
  const { portfolioId } = Route.useParams();

  return (
    <Container
      strategy="grid"
      size="lg"
      style={{ margin: 'var(--mantine-spacing-lg) var(--mantine-spacing-xl) var(--mantine-spacing-lg) var(--mantine-spacing-lg)' }}>
      <Stack>
        <Group justify="space-between" align="center">
          <Title>Portfolio</Title>

          <Menu position="bottom-end" shadow="xl">
            <Menu.Target>
              <Button size="xs" variant="default" c="gray.4" leftSection={<IconSettings size={14} />}>
                Settings
              </Button>
            </Menu.Target>
            <Menu.Dropdown p={0} bdrs="sm">
              <Menu.Item
                h={40}
                styles={{
                  item: { borderRadius: 'var(--mantine-radius-sm) var(--mantine-radius-sm) 0 0' }
                }}
                variant="subtle"
                color="gray"
                ta="left"
                leftSection={<IconPencilCode size={16} />}>
                Rename portfolio
              </Menu.Item>

              <Menu.Sub position="left-start">
                <Menu.Sub.Target>
                  <Menu.Sub.Item h={40} variant="subtle" color="gray" ta="left" leftSection={<IconWorld size={16} />}>
                    Change currency
                  </Menu.Sub.Item>
                </Menu.Sub.Target>
                <Menu.Sub.Dropdown>
                  {currencies?.map((c) => (
                    <Menu.Item key={c.value} color="gray" onClick={() => {}}>
                      {c.label}
                    </Menu.Item>
                  ))}
                </Menu.Sub.Dropdown>
              </Menu.Sub>

              <Menu.Item
                h={40}
                styles={{
                  item: { borderRadius: '0 0 var(--mantine-radius-sm) var(--mantine-radius-sm)' }
                }}
                variant="subtle"
                ta="left"
                leftSection={<IconArchive size={16} />}
                onClick={() => openArchiveModal(portfolioId)}>
                Archive portfolio
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
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
