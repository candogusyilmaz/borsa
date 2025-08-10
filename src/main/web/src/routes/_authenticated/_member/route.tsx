import { ActionIcon, AppShell, Button, Divider, Flex, Group, ScrollArea, Stack, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconBriefcase,
  IconCirclePlusFilled,
  IconCircleXFilled,
  IconHome,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand
} from '@tabler/icons-react';
import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link, linkOptions, Outlet, useParams } from '@tanstack/react-router';
import { queries } from '~/api';
import type { BasicPortfolioView } from '~/api/queries/types';
import { CreatePortfolioButton } from '~/components/Portfolio/CreatePortfolio';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';
import { UserNavbar } from '~/components/UserAvatarMenu';
import common from '~/styles/common.module.css';

export const Route = createFileRoute('/_authenticated/_member')({
  component: RouteComponent
});

const NAV_LINKS = [
  {
    label: 'Overview',
    icon: <IconHome size={20} />,
    options: linkOptions({
      to: '/overview'
    })
  }
];

function createPortfolioLink(portfolio: BasicPortfolioView) {
  return {
    label: portfolio.name,
    icon: <IconBriefcase size={20} />,
    options: linkOptions({
      to: '/portfolios/$portfolioId',
      params: { portfolioId: portfolio.id }
    })
  };
}

function RouteComponent() {
  const { portfolioId } = useParams({ strict: false });

  const [opened, { toggle }] = useDisclosure();
  const [mobileCollapsed, { toggle: toggleMobile }] = useDisclosure(true);
  const { open } = useTransactionModalStore();

  const { data: portfolioLinks } = useSuspenseQuery({
    ...queries.portfolio.fetchPortfolios(),
    select: (data) => data.map((portfolio) => createPortfolioLink(portfolio))
  });

  function closeSidebarOnMobileLinkClick() {
    if (!mobileCollapsed) {
      toggleMobile();
    }
  }

  return (
    <AppShell
      header={{ height: 50 }}
      navbar={{
        width: 280,
        breakpoint: 'sm',
        collapsed: { mobile: mobileCollapsed, desktop: opened }
      }}
      layout="alt">
      <AppShell.Header>
        <Flex align="center" h="100%" justify="space-between" px="md">
          <Group>
            <ActionIcon
              variant="subtle"
              color="gray.6"
              onClick={toggle}
              visibleFrom="sm"
              style={{
                height: '26px',
                width: '26px'
              }}>
              {opened ? (
                <IconLayoutSidebarLeftExpand style={{ width: '80%' }} />
              ) : (
                <IconLayoutSidebarLeftCollapse style={{ width: '80%' }} />
              )}
            </ActionIcon>
            <ActionIcon
              style={{
                height: '26px',
                width: '26px'
              }}
              variant="transparent"
              color="gray.6"
              onClick={toggleMobile}
              hiddenFrom="sm">
              <IconLayoutSidebarLeftExpand style={{ width: '80%' }} />
            </ActionIcon>
          </Group>
        </Flex>
      </AppShell.Header>
      <AppShell.Navbar bg="dark.8">
        <AppShell.Section>
          <Group h={60} align="center" mx="md" pt="sm" pb={3}>
            <UnstyledButton h="95%">
              <Group h="100%">
                <img
                  src="/assets/logo.png"
                  alt="Canverse"
                  style={{
                    maxWidth: '100%',
                    maxHeight: '100%',
                    objectFit: 'contain'
                  }}
                />
                <Text size="xl" fw={500} lts={1.7}>
                  CANVERSE
                </Text>
              </Group>
            </UnstyledButton>
            <ActionIcon
              hiddenFrom="sm"
              style={{
                height: '26px',
                width: '26px'
              }}
              variant="transparent"
              color="gray.6"
              onClick={toggleMobile}
              ml="auto"
              pb={3}>
              <IconLayoutSidebarLeftCollapse style={{ width: '80%' }} />
            </ActionIcon>
          </Group>
        </AppShell.Section>

        <AppShell.Section grow component={ScrollArea}>
          <Stack gap={8} p={0} mx="sm" mt="lg">
            <Text c="dimmed" fw={600} fz={12}>
              Quick Actions
            </Text>
            <Button
              justify={'left'}
              leftSection={<IconCirclePlusFilled size={18} color="var(--mantine-color-teal-5)" />}
              onClick={() => open('Buy')}
              className={common.quickActionLink}
              disabled={!portfolioId}>
              Buy Stock
            </Button>
            <Button
              justify={'left'}
              className={common.quickActionLink}
              leftSection={<IconCircleXFilled size={18} color="var(--mantine-color-red-5)" />}
              onClick={() => open('Sell')}
              disabled={!portfolioId}>
              Sell Stock
            </Button>
          </Stack>
          <Stack gap={4} p={0} mx="sm" mt="lg">
            <Text c="dimmed" fw={600} fz={12}>
              Menu
            </Text>
            {NAV_LINKS.map((s) => {
              return (
                <Link key={s.options.to} {...s.options} className={common.navLink} onClick={closeSidebarOnMobileLinkClick}>
                  <Group gap="xs" align="center">
                    {s.icon}
                    {s.label}
                  </Group>
                </Link>
              );
            })}
          </Stack>
          <Divider my="sm" mx="xs" />
          <Stack gap={4} p={0} mx="sm" mt="lg">
            <Text c="dimmed" fw={600} fz={12}>
              Portfolios
            </Text>
            <CreatePortfolioButton />
            {portfolioLinks.map((s) => {
              return (
                <Link
                  key={s.options.to + s.options.params.portfolioId}
                  {...s.options}
                  className={common.navLinkPortfolio}
                  onClick={closeSidebarOnMobileLinkClick}>
                  <Group gap="xs" align="center">
                    {s.icon}
                    {s.label}
                  </Group>
                </Link>
              );
            })}
          </Stack>
        </AppShell.Section>

        <AppShell.Section px="xs" py="sm">
          <UserNavbar />
        </AppShell.Section>
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
