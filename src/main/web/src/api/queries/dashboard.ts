import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { staleTimes } from './config';
import type { BasicDashboardView, DashboardView } from './types';

export const getAllDashboards = () =>
  queryOptions({
    queryKey: [`/dashboards`],
    queryFn: async ({ signal }) => (await http.get<BasicDashboardView[]>(`/dashboards`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev
  });

export const getDashboard = (id: string) =>
  queryOptions({
    queryKey: [`/dashboards`, id],
    queryFn: async ({ signal }) => (await http.get<DashboardView>(`/dashboards/${id}`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev
  });
