import { memberMutations } from './mutations/member';
import { stockMutations } from './mutations/stocks';
import { tradeMutations } from './mutations/trades';
import { analyticsQueries } from './queries/analytics';
import { portfolioQueries } from './queries/portfolio';
import { stockQueries } from './queries/stocks';
import { tradeQueries } from './queries/trades';

export const queries = {
  stocks: stockQueries,
  analytics: analyticsQueries,
  trades: tradeQueries,
  portfolio: portfolioQueries
};

export const mutations = {
  stocks: stockMutations,
  trades: tradeMutations,
  member: memberMutations
};
