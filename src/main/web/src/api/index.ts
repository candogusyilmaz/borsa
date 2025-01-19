import { stockMutations } from './mutations/stocks';
import { tradeMutations } from './mutations/trades';
import { memberQueries } from './queries/member';
import { stockQueries } from './queries/stocks';
import { tradeQueries } from './queries/trades';

export const queries = {
  stocks: stockQueries,
  trades: tradeQueries,
  member: memberQueries
};

export const mutations = {
  stocks: stockMutations,
  trades: tradeMutations
};
