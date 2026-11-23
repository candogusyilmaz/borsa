import { Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { PositionsView } from '~/features/positions/components/positions-view/positions-view';

export const Route = createFileRoute('/_authenticated/_member/positions')({
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; portfolioId?: number; q?: string }
});

function RouteComponent() {
  return (
    <>
      <Title order={1} mb="md" className="member-page-title">
        Positions
      </Title>
      <PositionsView />
    </>
  );
}
