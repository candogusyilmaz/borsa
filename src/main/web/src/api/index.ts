import { memberMutations } from './mutations/member';
import { stockMutations } from './mutations/stocks';
import { tradeMutations } from './mutations/trades';
import { analyticsQueries } from './queries/analytics';
import { memberQueries } from './queries/member';
import { stockQueries } from './queries/stocks';
import { tradeQueries } from './queries/trades';

export const queries = {
  stocks: stockQueries,
  analytics: analyticsQueries,
  member: memberQueries,
  trades: tradeQueries
};

export const mutations = {
  stocks: stockMutations,
  trades: tradeMutations,
  member: memberMutations
};
