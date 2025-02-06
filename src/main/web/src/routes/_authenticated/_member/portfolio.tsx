import { Flex, Space, Stack, Title } from "@mantine/core";
import { createFileRoute } from "@tanstack/react-router";
import { BalanceCard } from "~/components/Portfolio/BalanceCard";
import { TradeHistoryCard } from "~/components/Portfolio/TradeHistoryCard";
import { HoldingsCard } from "~/components/Portfolio/HoldingsCard";

export const Route = createFileRoute("/_authenticated/_member/portfolio")({
  component: RouteComponent,
});

function RouteComponent() {
  return (
    <Stack>
      <Title>Portfolio</Title>
      <Flex gap="md" direction={{ base: "column", sm: "row" }}>
        <BalanceCard />
        <HoldingsCard />
      </Flex>
      <Space />
      <TradeHistoryCard />
    </Stack>
  );
}
