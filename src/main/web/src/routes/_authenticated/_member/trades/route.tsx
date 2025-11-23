import { Container, Stack, Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { queries } from '~/api';
import { TradesTable } from './-components/trades-table/trades-table';

export const Route = createFileRoute('/_authenticated/_member/trades')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(queries.trades.fetchAllTransactions()),
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; q?: string }
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Title c="white">Trades</Title>
        <TradesTable />
      </Stack>
    </Container>
  );
}
