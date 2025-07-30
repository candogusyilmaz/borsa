import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { queryKeys, staleTimes } from './config';
import type { BasicPortfolioView, PortfolioInfo } from './types';

export const fetchPortfolio = ({ portfolioId }: { portfolioId: number }) =>
  queryOptions({
    queryKey: ['/portfolios', portfolioId, queryKeys.portfolio],
    queryFn: async ({ signal }) =>
      (
        await http.get<PortfolioInfo>(`/portfolios/${portfolioId}`, {
          signal
        })
      ).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev
  });

export const fetchPortfolios = () =>
  queryOptions({
    queryKey: ['/portfolios'],
    queryFn: async ({ signal }) => (await http.get<BasicPortfolioView[]>(`/portfolios`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev
  });
