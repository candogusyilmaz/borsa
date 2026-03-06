import * as accountMutations from '~/api/mutations/account';
import * as dashboardMutations from '~/api/mutations/dashboard';
import * as tradeMutations from '~/api/mutations/trades';

export const mutations = {
  trades: tradeMutations,
  account: accountMutations,
  dashboard: dashboardMutations
};
