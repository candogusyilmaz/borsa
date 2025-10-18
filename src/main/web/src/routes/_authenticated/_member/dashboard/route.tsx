import { Button, Container, Divider, Group, Menu, SimpleGrid, Skeleton, Stack, Title } from '@mantine/core';
import {
  IconCheck,
  IconChevronDown,
  IconLayoutDashboardFilled,
  IconListCheck,
  IconPencilCode,
  IconSettings,
  IconTrash,
  IconWorld
} from '@tabler/icons-react';
import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import { useCreateNewDashboardModalStore } from '~/components/Dashboard/CreateNewDashboardModal';
import { DailyChangeCard } from './-components/daily-change-card';
import { RealizedGainsCard } from './-components/realized-gains-card';
import { TotalBalanceCard } from './-components/total-balance-card';
import { TransactionsChart } from './-components/transactions-chart';

export const Route = createFileRoute('/_authenticated/_member/dashboard')({
  component: RouteComponent
});

function RouteComponent() {
  const { data: dashboards } = useSuspenseQuery(queries.dashboard.getAllDashboards());
  const [selectedDashboard, setSelectedDashboard] = useState(dashboards.find((s) => s.isDefault)?.id ?? dashboards[0].id);
  const { data: dashboard, status } = useQuery(queries.dashboard.getDashboard(selectedDashboard));
  const { data: currencies } = useQuery(queries.currency.getAllCurrencies());
  const open = useCreateNewDashboardModalStore((s) => s.open);

  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Group justify="space-between" align="center">
          <Menu width={250} position="bottom-start" shadow="xl">
            <Menu.Target>
              <Button size="compact-xl" variant="subtle" px={5} ml={-5} color="gray" rightSection={<IconChevronDown size={20} />}>
                <Title component="span" c="white">
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
                Rename dashboard
              </Menu.Item>
              <Menu.Item h={40} variant="subtle" color="gray" ta="left" leftSection={<IconListCheck size={16} />}>
                Select portfolios
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
                color="red"
                ta="left"
                leftSection={<IconTrash size={16} />}
                disabled={dashboards.find((d) => d.id === selectedDashboard)?.isDefault}>
                Delete dashboard
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
        {status === 'success' && (
          <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }} spacing="md" mih={125}>
            <TotalBalanceCard data={dashboard.totalBalance} />
            <DailyChangeCard data={dashboard.dailyChange} />
            <RealizedGainsCard rgd={dashboard.realizedGains} />
          </SimpleGrid>
        )}
        {status === 'pending' && <Skeleton height="80vh" />}

        <SimpleGrid cols={{ base: 1 }} mt="md">
          {status === 'success' && <TransactionsChart currencyCode={dashboard.realizedGains.currencyCode} dashboardId={dashboard.id} />}
          {status === 'pending' && <Skeleton height="80vh" />}
        </SimpleGrid>
      </Stack>
    </Container>
  );
}
