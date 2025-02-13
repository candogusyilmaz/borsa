import { Flex, Space, Stack, Title } from "@mantine/core";
import { createFileRoute } from "@tanstack/react-router";
import { BalanceCard } from "~/components/Portfolio/BalanceCard";
import { HoldingsCard } from "~/components/Portfolio/HoldingsCard";
import { RecentTransactionsCard } from "~/components/Portfolio/RecentTransactionsCard";
import { MonthlyRevenueOverview } from "~/components/Portfolio/MonthlyRevenueOverview";

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
      <MonthlyRevenueOverview />
      <RecentTransactionsCard />
    </Stack>
  );
}
