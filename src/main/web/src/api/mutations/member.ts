import {http} from '~/lib/axios';

export const accountMutations = {
    clearMyData: {
        mutationFn: () => http.post('/account/clear-my-data')
    }
};
