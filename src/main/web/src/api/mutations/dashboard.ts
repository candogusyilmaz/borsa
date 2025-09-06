import { http } from '~/lib/axios';
import type { CreateDashboardRequest } from './types';

export const create = {
  mutationFn: (body: CreateDashboardRequest) => http.post(`/dashboards`, body)
};
