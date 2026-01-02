import {$api} from '~/api/openapi';

export function usePortfolioName(portfolioId: string) {
    const {data} = $api.useQuery('get', '/api/portfolios');

    return data?.find((p) => String(p.id) === portfolioId)?.name || '';
}
