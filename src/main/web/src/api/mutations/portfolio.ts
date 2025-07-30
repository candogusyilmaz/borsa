import { http } from '~/lib/axios';

export const createPortfolio = {
  mutationFn: (data: { name: string }) => http.post('/portfolios', data)
};
