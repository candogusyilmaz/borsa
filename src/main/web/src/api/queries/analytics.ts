import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { queryKeys, staleTimes } from './config';
import type { MonthlyRevenueOverview } from './types';

export const monthlyRevenueOverview = ({ portfolioId }: { portfolioId: number }) =>
  queryOptions({
    queryKey: [`/portfolios/${portfolioId}/analytics/monthly-revenue-overview`, queryKeys.portfolio],
    queryFn: async ({ signal }) =>
      (await http.get<MonthlyRevenueOverview>(`/portfolios/${portfolioId}/analytics/monthly-revenue-overview`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES
  });
