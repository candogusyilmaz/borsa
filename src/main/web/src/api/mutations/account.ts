import { http } from '~/lib/axios';

export const clearMyData = {
  mutationFn: () => http.post('/account/clear-my-data')
};
