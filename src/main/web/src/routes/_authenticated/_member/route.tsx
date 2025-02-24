import { AppShell, Burger, Container, Group, Title } from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import {
  Link,
  Outlet,
  createFileRoute,
  linkOptions,
} from "@tanstack/react-router";
import classes from "~/styles/MemberLayout.module.css";

export const Route = createFileRoute("/_authenticated/_member")({
  component: RouteComponent,
});

const NAV_LINKS = [
  {
    label: "Portfolio",
    options: linkOptions({
      to: "/portfolio",
    }),
  },
  {
    label: "Stocks",
    options: linkOptions({
      to: "/stocks",
    }),
  },
];

function RouteComponent() {
  const [opened, { toggle }] = useDisclosure();

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{
        width: 300,
        breakpoint: "sm",
        collapsed: { desktop: true, mobile: !opened },
      }}
      padding="md"
    >
      <AppShell.Header>
        <Container h="100%">
          <Group h="100%">
            <Burger
              opened={opened}
              onClick={toggle}
              hiddenFrom="sm"
              size="sm"
            />
            <Group justify="space-between" w="100%">
              <Title size="md" c="gray">
                Canverse
              </Title>
              <Group ml="xl" gap={0} visibleFrom="sm">
                {NAV_LINKS.map((s) => {
                  return (
                    <Link
                      key={s.options.to}
                      {...s.options}
                      className={classes.control}
                    >
                      {s.label}
                    </Link>
                  );
                })}
              </Group>
            </Group>
          </Group>
        </Container>
      </AppShell.Header>

      <AppShell.Navbar py="md" px={4}>
        {NAV_LINKS.map((s) => {
          return (
            <Link
              key={s.options.to}
              {...s.options}
              className={classes.control}
              onClick={toggle}
            >
              {s.label}
            </Link>
          );
        })}
      </AppShell.Navbar>

      <AppShell.Main>
        <Container>
          <Outlet />
        </Container>
      </AppShell.Main>
    </AppShell>
  );
}
