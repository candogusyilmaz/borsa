import { Box, Card, Group, Stack, Text, Tooltip, rem } from "@mantine/core";
import { IconCircle } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { queries } from "~/api";
import { constants } from "~/lib/constants";
import { format } from "~/lib/format";

export function TradesHeatMap() {
  const { data } = useSuspenseQuery(queries.trades.heatMap());

  const monthShortNames = Array.from({ length: 12 }, (_, index) =>
    new Intl.DateTimeFormat(constants.locale(), { month: "short" }).format(
      new Date(2000, index, 1)
    )
  );

  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  const fullData = data.map((yearData) => {
    const filledMonths = months.map((month) => {
      const existing = yearData.data.find((m) => m.month === month);
      return existing || { month, profit: 0 };
    });

    return { year: yearData.year, data: filledMonths };
  });

  if (data.length === 0) {
    return (
      <>
        <Text fw={700} size={rem(22)}>
          Monthly Revenue Overview
        </Text>
        <Card shadow="sm" radius="md" p="lg">
          <Text c="dimmed" size="xs" fw={600} ta="center">
            You haven't{" "}
            <Text span inherit c="red">
              sold
            </Text>{" "}
            any shares.
          </Text>
        </Card>
      </>
    );
  }

  return (
    <>
      <Text fw={700} size={rem(22)}>
        Monthly Revenue Overview
      </Text>
      <Card shadow="sm" radius="md" p="lg">
        <Stack justify="center" h="100%">
          {fullData.map((s) => (
            <Group key={`${s.year}`} justify="center" grow>
              <Text>{s.year}</Text>
              {s.data.map((m) => (
                <Tooltip
                  key={`${m.month}-${s.year}`}
                  label={
                    m.profit === 0
                      ? "No data found"
                      : format.toCurrency(m.profit)
                  }
                >
                  <Box
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      height: 35,
                      padding: "10px",
                      backgroundColor:
                        m.profit === 0
                          ? "var(--mantine-color-gray-8)"
                          : m.profit > 0
                            ? "var(--mantine-color-teal-9)"
                            : "var(--mantine-color-red-9)",
                      borderRadius: "5px",
                      cursor: "pointer",
                      minWidth: "50px",
                    }}
                  >
                    {m.profit === 0 ? (
                      <IconCircle size="18px" />
                    ) : (
                      <Text
                        fz="xs"
                        fw={400}
                      >{`${m.profit > 0 ? "+" : ""}${format.toHumanizedCurrency(m.profit)}`}</Text>
                    )}
                  </Box>
                </Tooltip>
              ))}
            </Group>
          ))}
          <Group align="center" grow>
            <Box style={{ width: 50 }} />
            {monthShortNames.map((name) => (
              <Text
                key={name}
                size="xs"
                style={{ minWidth: 50, textAlign: "center" }}
              >
                {name}
              </Text>
            ))}
          </Group>
        </Stack>
      </Card>
    </>
  );
}
