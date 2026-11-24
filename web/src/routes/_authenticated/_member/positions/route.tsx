import { createFileRoute } from '@tanstack/react-router';
import { PositionsPage } from '~/features/positions/pages/positions-page';

export const Route = createFileRoute('/_authenticated/_member/positions')({
  component: PositionsPage,
  validateSearch: () => ({}) as { page?: number; portfolioId?: number; q?: string }
});
