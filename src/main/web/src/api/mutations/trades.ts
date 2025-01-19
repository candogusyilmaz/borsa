import { http } from '~/lib/axios';

export const tradeMutations = {
  buy: {
    mutationFn: (body: unknown) => http.post('/trades/buy', body)
  },
  sell: {
    mutationFn: (body: unknown) => http.post('/trades/sell', body)
  }
};
