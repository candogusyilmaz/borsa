import * as accountMutations from '~/api/mutations/account';
import * as dashboardMutations from '~/api/mutations/dashboard';
import * as portfolioMutations from '~/api/mutations/portfolio';
import * as stockMutations from '~/api/mutations/stocks';
import * as tradeMutations from '~/api/mutations/trades';

import * as dashboard from '~/api/queries/dashboard';
import * as portfolio from '~/api/queries/portfolio';
import * as position from '~/api/queries/position';
import * as trades from '~/api/queries/trades';

export const queries = {
  portfolio,
  trades,
  dashboard,
  position
};

export const mutations = {
  stocks: stockMutations,
  trades: tradeMutations,
  account: accountMutations,
  portfolio: portfolioMutations,
  dashboard: dashboardMutations
};
