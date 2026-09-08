import { Container, Stack, Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { TradesTable } from './-components/trades-table/trades-table';

export const Route = createFileRoute('/_authenticated/_member/trades')({
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; q?: string }
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" p="lg">
      <Stack>
        <Title>Trades</Title>
        <TradesTable />
      </Stack>
    </Container>
  );
}
