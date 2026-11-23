import { $api } from '~/api/openapi';

export function usePositions(portfolioId?: number) {
  return $api.useQuery('get', '/api/positions', {
    params: { query: { portfolioId } }
  });
}
