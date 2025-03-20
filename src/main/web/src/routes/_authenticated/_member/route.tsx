import {ActionIcon, AppShell, Flex, Group, Stack, Text, TextInput, UnstyledButton,} from "@mantine/core";
import {useDisclosure} from "@mantine/hooks";
import {
  IconExchange,
  IconHome,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand,
  IconSearch,
} from "@tabler/icons-react";
import {createFileRoute, Link, linkOptions, Outlet,} from "@tanstack/react-router";
import {UserAvatarMenu} from "~/components/UserAvatarMenu";
import common from "~/styles/common.module.css";

export const Route = createFileRoute("/_authenticated/_member")({
    component: RouteComponent,
});

const NAV_LINKS = [
    {
        label: "Portfolio",
        icon: <IconHome size={20}/>,
        options: linkOptions({
            to: "/portfolio",
        }),
    },
    {
        label: "Stocks",
        icon: <IconExchange size={20}/>,
        options: linkOptions({
            to: "/stocks",
        }),
    },
];

function RouteComponent() {
    const [opened, {toggle}] = useDisclosure();
    const [mobileOpened, {toggle: toggleMobile}] = useDisclosure(true);

    return (
        <AppShell
            header={{height: 60}}
            navbar={{
                width: 280,
                breakpoint: "sm",
                collapsed: {mobile: mobileOpened, desktop: opened},
            }}
            layout="alt"
        >
            <AppShell.Header>
                <Flex align="center" h="100%" justify="space-between" px="md">
                    <Group>
                        <ActionIcon
                            size="sm"
                            variant="transparent"
                            color="gray.6"
                            onClick={toggle}
                            visibleFrom="sm"
                        >
                            {opened ? (
                                <IconLayoutSidebarLeftExpand style={{width: "100%"}}/>
                            ) : (
                                <IconLayoutSidebarLeftCollapse style={{width: "100%"}}/>
                            )}
                        </ActionIcon>
                        <ActionIcon
                            size="sm"
                            variant="transparent"
                            color="gray.6"
                            onClick={toggleMobile}
                            hiddenFrom="sm"
                        >
                            <IconLayoutSidebarLeftExpand style={{width: "100%"}}/>
                        </ActionIcon>
                        <TextInput
                            fz="lg"
                            variant="unstyled"
                            placeholder="Search..."
                            leftSection={<IconSearch size={20}/>}
                        />
                    </Group>

                    <UserAvatarMenu/>
                </Flex>
            </AppShell.Header>
            <AppShell.Navbar bg="dark.8">
                <Stack>
                    <Group h={60} align="center" mx="md" pt="sm" pb={3}>
                        <UnstyledButton h="95%">
                            <img
                                src="/assets/logo.png"
                                alt="Canverse"
                                style={{
                                    maxWidth: "100%",
                                    maxHeight: "100%",
                                    objectFit: "contain",
                                }}
                            />
                        </UnstyledButton>
                        <Text size="xl" fw={500} lts={1.7}>
                            CANVERSE
                        </Text>
                        <ActionIcon
                            hiddenFrom="sm"
                            size="sm"
                            variant="transparent"
                            color="gray.6"
                            onClick={toggleMobile}
                            ml="auto"
                            pb={3}
                        >
                            <IconLayoutSidebarLeftCollapse style={{width: "100%"}}/>
                        </ActionIcon>
                    </Group>

                    <Stack gap={8} p={0} mx="sm" mt="lg">
                        <Text c="dimmed" fw={700} fz={12} lts={1.4}>
                            MENU
                        </Text>
                        {NAV_LINKS.map((s) => {
                            return (
                                <Link
                                    key={s.options.to}
                                    {...s.options}
                                    className={common.navLink}
                                >
                                    <Group align="center">
                                        {s.icon}
                                        {s.label}
                                    </Group>
                                </Link>
                            );
                        })}
                    </Stack>
                </Stack>
            </AppShell.Navbar>
            <AppShell.Main>
                <Outlet/>
            </AppShell.Main>
        </AppShell>
    );
}
