import * as accountMutations from '~/api/mutations/account';
import * as stockMutations from '~/api/mutations/stocks';
import * as tradeMutations from '~/api/mutations/trades';

import * as analytics from '~/api/queries/analytics';
import * as portfolio from '~/api/queries/portfolio';
import * as stocks from '~/api/queries/stocks';
import * as trades from '~/api/queries/trades';

export const queries = {
    portfolio,
    analytics,
    trades,
    stocks
};

export const mutations = {
    stocks: stockMutations,
    trades: tradeMutations,
    account: accountMutations
};
