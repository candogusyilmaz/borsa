import * as accountMutations from '~/api/mutations/account';
import * as dashboardMutations from '~/api/mutations/dashboard';
import * as portfolioMutations from '~/api/mutations/portfolio';
import * as tradeMutations from '~/api/mutations/trades';

import * as dashboard from '~/api/queries/dashboard';
import * as portfolio from '~/api/queries/portfolio';
import * as trades from '~/api/queries/trades';

export const queries = {
  portfolio,
  trades,
  dashboard
};

export const mutations = {
  trades: tradeMutations,
  account: accountMutations,
  portfolio: portfolioMutations,
  dashboard: dashboardMutations
};
