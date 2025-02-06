import { Flex, Space, Stack, Title } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { queries } from "~/api";
import { BalanceCard } from "~/components/Portfolio/BalanceCard";
import "@mantine/charts/styles.css";
import { TradeHistoryCard } from "~/components/Portfolio/TradeHistoryCard";
import { HoldingsCard } from "~/components/Portfolio/HoldingsCard";

export const Route = createFileRoute("/_authenticated/_member/portfolio")({
  component: RouteComponent,
});

function RouteComponent() {
  const { data: balance } = useQuery(queries.member.balance());
  const { data: tradeHistory } = useQuery(queries.member.tradeHistory());

  return (
    <Stack>
      <Title>Portfolio</Title>
      <Flex gap="md" direction={{ base: "column", sm: "row" }}>
        {balance && <BalanceCard data={balance} />}
        {balance && <HoldingsCard data={balance} />}
      </Flex>
      {tradeHistory && (
        <>
          <Space />
          <TradeHistoryCard data={tradeHistory} />
        </>
      )}
    </Stack>
  );
}
