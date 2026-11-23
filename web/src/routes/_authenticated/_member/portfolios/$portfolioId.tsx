import { createFileRoute } from '@tanstack/react-router';
import { PortfolioPage } from '~/features/portfolios/pages/portfolio-page';

export const Route = createFileRoute('/_authenticated/_member/portfolios/$portfolioId')({
  component: RouteComponent,
  validateSearch: () => ({}) as { page?: number; q?: string }
});

function RouteComponent() {
  const { portfolioId } = Route.useParams();
  return <PortfolioPage portfolioId={portfolioId} />;
}
