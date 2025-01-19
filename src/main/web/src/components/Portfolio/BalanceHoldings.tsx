import { ActionIcon, Card, Group, Stack, Text } from "@mantine/core";
import type { Balance } from "~/api/queries/types";
import Currency from "~/components/Currency";
import { useState } from "react";
import { format } from "~/lib/format";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";

const PAGE_SIZE = 3;

export function BalanceHoldings({ data: { stocks } }: { data: Balance }) {
  const [currentPage, setCurrentPage] = useState(1);

  const totalPages = Math.ceil(stocks.length / PAGE_SIZE);

  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const endIndex = startIndex + PAGE_SIZE;
  const currentStocks = stocks.slice(startIndex, endIndex);

  const handleNextPage = () => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1);
    }
  };

  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1);
    }
  };

  return (
    <Stack justify="center" gap="md" mt="xl">
      <Group justify="space-between">
        <ActionIcon
          variant="transparent"
          c={currentPage === 1 ? "dimmed" : "white"}
          onClick={handlePrevPage}
          disabled={currentPage === 1}
        >
          <IconChevronLeft />
        </ActionIcon>
        <Text size="sm" fw={500}>
          {`${currentPage}/${totalPages}`}
        </Text>
        <ActionIcon
          variant="transparent"
          c={currentPage === totalPages ? "dimmed" : "white"}
          onClick={handleNextPage}
          disabled={currentPage === totalPages}
        >
          <IconChevronRight />
        </ActionIcon>
      </Group>
      {currentStocks.map((stock) => (
        <Card shadow="0" key={stock.symbol} withBorder padding="sm" radius="sm">
          <Group justify="space-between">
            <Text>{stock.symbol}</Text>
            <Currency fw={500}>{stock.value}</Currency>
          </Group>
          <Group justify="space-between">
            <Text size="xs" c="dimmed">
              {stock.quantity} shares @{" "}
              <Currency span>{stock.averagePrice}</Currency>
            </Text>
            <Group>
              <Text span size="xs" c={stock.profit >= 0 ? "teal" : "red"}>
                {format.toLocalePercentage(stock.profitPercentage / 100)}
              </Text>
            </Group>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}
