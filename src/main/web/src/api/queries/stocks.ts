import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';

export const stockQueries = {
  lookup: (exchange?: string) =>
    queryOptions({
      queryKey: ['/stocks', exchange],
      queryFn: async () => (await http.get('/stocks', { params: { exchange } })).data
    })
};
