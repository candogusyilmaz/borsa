import { Button, Container, Divider, Menu, SimpleGrid, Skeleton, Stack, Title } from '@mantine/core';
import {
  IconCashBanknote,
  IconChartLine,
  IconCheck,
  IconChevronDown,
  IconLayoutDashboardFilled,
  IconTrendingDown,
  IconTrendingUp
} from '@tabler/icons-react';
import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import { useCreateNewDashboardModalStore } from '~/components/Dashboard/CreateNewDashboardModal';
import { StatCard } from './-components/stat-card';
import { TransactionsChart } from './-components/transactions-chart';

export const Route = createFileRoute('/_authenticated/_member/dashboard')({
  component: RouteComponent,
  loader: async ({ context: { queryClient } }) => {
    await queryClient.ensureQueryData(queries.dashboard.getAllDashboards());
  }
});

function RouteComponent() {
  const { data: dashboards } = useSuspenseQuery(queries.dashboard.getAllDashboards());
  const [selectedDashboard, setSelectedDashboard] = useState(dashboards.find((s) => s.isDefault)?.id ?? dashboards[0]!.id);
  const { data: dashboard, status } = useQuery(queries.dashboard.getDashboard(selectedDashboard));
  const open = useCreateNewDashboardModalStore((s) => s.open);

  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Menu width={250} position="bottom-start" shadow="xl">
          <Menu.Target>
            <Button mr="auto" size="compact-xl" variant="subtle" px={5} ml={-5} color="gray" rightSection={<IconChevronDown size={20} />}>
              <Title component="span" c={'var(--text-main)'}>
                {dashboards.find((d) => d.id === selectedDashboard)?.name}
              </Title>
            </Button>
          </Menu.Target>
          <Menu.Dropdown ml={5} p={0} bdrs="sm">
            {dashboards.map((d, index) => (
              <Menu.Item
                styles={{
                  item: { borderRadius: index === 0 ? 'var(--mantine-radius-sm) var(--mantine-radius-sm) 0 0' : '0' }
                }}
                h={50}
                key={d.id}
                color="gray"
                bg={d.id === selectedDashboard ? 'dark.7' : undefined}
                leftSection={d.id === selectedDashboard ? <IconCheck color="green" size={16} /> : null}
                onClick={() => setSelectedDashboard(d.id)}>
                {d.name}
              </Menu.Item>
            ))}
            <Divider my={0} color="dark.4" />
            <Menu.Item
              styles={{
                item: { borderRadius: '0 0 var(--mantine-radius-sm) var(--mantine-radius-sm)' }
              }}
              h={50}
              variant="subtle"
              color="gray"
              ta="left"
              leftSection={<IconLayoutDashboardFilled color="#3195ebc7" size={16} />}
              onClick={open}>
              Create new dashboard
            </Menu.Item>
          </Menu.Dropdown>
        </Menu>
        {status === 'success' && (
          <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }} spacing="md" mih={125}>
            <StatCard
              title="Total Balance"
              subtitle="All-Time Growth"
              displayValue={dashboard.totalBalance.value}
              percentageChange={dashboard.totalBalance.percentageChange}
              currencyCode={dashboard.totalBalance.currencyCode}
              icon={<IconChartLine />}
            />
            <StatCard
              title="Daily Change"
              subtitle="Daily Performance"
              displayValue={dashboard.dailyChange.currentValue - dashboard.dailyChange.previousValue}
              percentageChange={dashboard.dailyChange.percentageChange}
              currencyCode={dashboard.dailyChange.currencyCode}
              icon={dashboard.dailyChange.percentageChange! >= 0 ? <IconTrendingUp /> : <IconTrendingDown />}
            />
            <StatCard
              title="Realized P&L"
              subtitle={dashboard.realizedGains.percentageChange ? 'Current Month' : undefined}
              displayValue={dashboard.realizedGains.currentPeriod}
              percentageChange={dashboard.realizedGains.percentageChange}
              currencyCode={dashboard.realizedGains.currencyCode}
              statusValue={dashboard.realizedGains.currentPeriod} // Uses currentPeriod value for icon color
              icon={<IconCashBanknote />}
            />
          </SimpleGrid>
        )}
        <SimpleGrid cols={{ base: 1 }} mt="md">
          {status === 'success' && <TransactionsChart currencyCode={dashboard.realizedGains.currencyCode} dashboardId={dashboard.id} />}
          {status === 'pending' && <Skeleton height="80vh" />}
        </SimpleGrid>
      </Stack>
    </Container>
  );
}
