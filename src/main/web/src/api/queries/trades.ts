import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { TradesHeatMap } from './types';

export const tradeQueries = {
  heatMap: () =>
    queryOptions({
      queryKey: ['/trades/heat-map'],
      queryFn: async ({ signal }) => (await http.get<TradesHeatMap>('/trades/heat-map', { signal })).data
    })
};
