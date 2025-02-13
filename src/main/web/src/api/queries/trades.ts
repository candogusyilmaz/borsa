import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { MonthlyRevenueOverview } from './types';

export const tradeQueries = {
  monthlyRevenueOverview: () =>
    queryOptions({
      queryKey: ['/analytics/monthly-revenue-overview'],
      queryFn: async ({ signal }) => (await http.get<MonthlyRevenueOverview>('/analytics/monthly-revenue-overview', { signal })).data
    })
};
