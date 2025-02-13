import { http } from '~/lib/axios';

export const memberMutations = {
  clearMyData: {
    mutationFn: () => http.post('/member/clear-my-data')
  }
};
