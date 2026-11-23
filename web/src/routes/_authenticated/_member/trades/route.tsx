import { Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { TradesList } from '~/features/trades/components/trades-list/trades-list';

export const Route = createFileRoute('/_authenticated/_member/trades')({
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; q?: string }
});

function RouteComponent() {
  return (
    <>
      <Title order={1} mb="md" className="member-page-title">
        Trades
      </Title>
      <TradesList />
    </>
  );
}
