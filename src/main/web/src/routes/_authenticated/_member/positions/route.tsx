import { Container, Stack, Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { queries } from '~/api';
import { PositionsFilter } from './-components/positions-table/positions-filter';
import { PositionsTable } from './-components/positions-table/positions-table';

export const Route = createFileRoute('/_authenticated/_member/positions')({
  loader: ({ context: { queryClient } }) => queryClient.ensureQueryData(queries.position.fetchPositions()),
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; portfolioId?: number; q?: string }
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Title c="white">Positions</Title>
        <PositionsFilter />
        <PositionsTable />
      </Stack>
    </Container>
  );
}
