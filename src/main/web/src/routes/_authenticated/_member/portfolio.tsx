import { Card, Group, Stack, Title } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { queries } from "~/api";
import { BalanceCard } from "~/components/Portfolio/Balance";

export const Route = createFileRoute("/_authenticated/_member/portfolio")({
  component: RouteComponent,
});

function RouteComponent() {
  const { data: balance } = useQuery(queries.member.balance());

  return (
    <Stack>
      <Title>Portfolio</Title>
      <Group align="stretch">
        {balance && <BalanceCard data={balance} />}
        <Card shadow="sm" padding="lg" radius="md" withBorder>
          asdg
        </Card>
      </Group>
    </Stack>
  );
}
