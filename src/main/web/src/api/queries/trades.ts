import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { MonthlyRevenueOverview } from './types';
import { queryKeys } from './keys';

export const tradeQueries = {
  monthlyRevenueOverview: () =>
    queryOptions({
      queryKey: ['/analytics/monthly-revenue-overview', queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<MonthlyRevenueOverview>('/analytics/monthly-revenue-overview', { signal })).data
    })
};
