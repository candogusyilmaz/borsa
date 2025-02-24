import { DonutChart } from "@mantine/charts";
import {
  Box,
  Card,
  type CardProps,
  Divider,
  Group,
  Stack,
  Text,
  rem,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { queries } from "~/api";
import type { Balance } from "~/api/queries/types";
import { format } from "~/lib/format";
import Currency from "../Currency";
import { ErrorView } from "../ErrorView";
import { LoadingView } from "../LoadingView";

const COLORS = [
  { id: 0, color: "#0066ff" },
  { id: 1, color: "#e85dff" },
  { id: 2, color: "#ffb800" },
];

export function BalanceCard() {
  const { data, status } = useQuery(queries.member.balance());

  if (status === "pending") {
    return (
      <BalanceContainer miw={275} mih={325}>
        <LoadingView />
      </BalanceContainer>
    );
  }

  if (status === "error") {
    return (
      <BalanceContainer
        miw={275}
        mih={325}
        style={{ borderColor: "var(--mantine-color-red-5)" }}
      >
        <ErrorView />
      </BalanceContainer>
    );
  }

  return <Inner data={data} />;
}

function BalanceContainer({ children, ...props }: CardProps) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder {...props}>
      {children}
    </Card>
  );
}

function Inner({ data }: { data: Balance }) {
  const [activeSegment, setActiveSegment] = useState<{
    name: string;
    value: number;
    percent: number;
  } | null>(null);

  const sortedStocks = [...data.stocks].sort((a, b) => b.value - a.value);

  const filteredStocks = sortedStocks.filter(
    (stock) => stock.value / data.totalValue >= 0.01
  );

  // Select up to the top 3 stocks
  const topStocks = filteredStocks.slice(0, 3);

  // Calculate "Other" category
  const otherStocks = filteredStocks.slice(3); // Remaining stocks after top 3
  const otherValue = otherStocks.reduce((sum, stock) => sum + stock.value, 0);

  const pieData = [
    ...topStocks.map((stock, idx) => ({
      name: stock.symbol,
      value: stock.value,
      color: COLORS.find((s) => s.id === idx)?.color!,
    })),
    ...(otherValue > 0
      ? [
          {
            name: "Other",
            value: otherValue,
            color: "lightgray",
          },
        ]
      : []),
  ];
  const handleMouseEnter = (segment: {
    name: string;
    value: number;
    percent: number;
  }) => {
    setActiveSegment(segment);
  };

  const handleMouseLeave = () => {
    setActiveSegment(null);
  };
  return (
    <BalanceContainer>
      <Group justify="center">
        <Box pos="relative">
          <DonutChart
            data={pieData}
            size={250}
            thickness={15}
            paddingAngle={3}
            pieProps={{
              cornerRadius: 5,
              onMouseEnter: (segment) => handleMouseEnter(segment),
              onMouseLeave: handleMouseLeave,
            }}
            withTooltip={false}
          />
          <Stack
            style={{
              position: "absolute",
              top: "50%",
              left: "50%",
              transform: "translate(-50%, -50%)",
              textAlign: "center",
            }}
            gap={8}
          >
            {!activeSegment ? (
              <>
                <Text c="gray.3" fz={rem(18)} fw={700}>
                  Balance
                </Text>
                <Currency fz={rem(22)} fw={500}>
                  {data.totalValue}
                </Currency>
                {data.stocks.length !== 0 && (
                  <Text
                    fz="sm"
                    fw={700}
                    c={data.totalProfitPercentage >= 0 ? "teal" : "red"}
                  >
                    {`${data.totalProfit > 0 ? "+" : ""}${format.toCurrency(data.totalProfit)}`}
                  </Text>
                )}
              </>
            ) : (
              <>
                <Text size={rem(22)} fw={700}>
                  {activeSegment.name}
                </Text>
                <Text size={rem(18)} fw={500} c="teal">
                  {format.toLocalePercentage(activeSegment.percent * 100)}
                </Text>
              </>
            )}
          </Stack>
        </Box>
      </Group>
      {topStocks.length > 0 && (
        <Stack justify="center" gap="sm" mt="xl">
          <Text c="dimmed" ta="center" size="sm" fw={700}>
            Top Holdings
          </Text>
          {topStocks.map((stock, idx) => (
            <Stack key={stock.symbol} gap="sm" justify="center">
              <Box>
                <Group justify="space-between">
                  <Text>{stock.symbol}</Text>
                  <Currency fw={500}>{stock.value}</Currency>
                </Group>
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    {format.toHumanizedNumber(stock.quantity)} shares @{" "}
                    <Currency span>{stock.averagePrice}</Currency>
                  </Text>
                  <Group>
                    <Text
                      span
                      size="xs"
                      c={
                        stock.profit === 0
                          ? "dimmed"
                          : stock.profit >= 0
                            ? "teal"
                            : "red"
                      }
                    >
                      {format.toLocalePercentage(stock.profitPercentage)}
                    </Text>
                  </Group>
                </Group>
              </Box>
              {idx !== topStocks.length - 1 && <Divider color="gray" />}
            </Stack>
          ))}
        </Stack>
      )}
    </BalanceContainer>
  );
}
