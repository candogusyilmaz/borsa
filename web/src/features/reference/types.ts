import type { components } from '@/api/schema';

export type Country = components['schemas']['CountryResponse'];
export type Currency = components['schemas']['CurrencyResponse'];
export type Market = components['schemas']['MarketResponse'];
export type MarketCalendar = components['schemas']['MarketCalendarResponse'];
export type MarketCalendarSession = components['schemas']['MarketCalendarSessionResponse'];
export type CalendarCoverageStatus = components['schemas']['MarketCalendarResponse']['coverageStatus'];
