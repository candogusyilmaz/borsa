import {
  AppShell,
  Group,
  Burger,
  UnstyledButton,
  Container,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { createFileRoute, Outlet } from "@tanstack/react-router";
import classes from "~/styles/MemberLayout.module.css";

export const Route = createFileRoute("/_authenticated/_member")({
  component: RouteComponent,
});

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
            <Group justify="space-between" style={{ flex: 1 }}>
              <div>test</div>
              <Group ml="xl" gap={0} visibleFrom="sm">
                <UnstyledButton className={classes.control}>
                  Home
                </UnstyledButton>
                <UnstyledButton className={classes.control}>
                  Blog
                </UnstyledButton>
                <UnstyledButton className={classes.control}>
                  Contacts
                </UnstyledButton>
                <UnstyledButton className={classes.control}>
                  Support
                </UnstyledButton>
              </Group>
            </Group>
          </Group>
        </Container>
      </AppShell.Header>

      <AppShell.Navbar py="md" px={4}>
        <UnstyledButton className={classes.control}>Home</UnstyledButton>
        <UnstyledButton className={classes.control}>Blog</UnstyledButton>
        <UnstyledButton className={classes.control}>Contacts</UnstyledButton>
        <UnstyledButton className={classes.control}>Support</UnstyledButton>
      </AppShell.Navbar>

      <AppShell.Main>
        <Container>
          <Outlet />
        </Container>
      </AppShell.Main>
    </AppShell>
  );
}
