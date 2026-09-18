import type { components } from '@/api/schema';

export type InstrumentSummary = components['schemas']['InstrumentSummaryResponse'];
export type InstrumentDetail = components['schemas']['InstrumentResponse'];
export type InstrumentAlias = components['schemas']['InstrumentAliasResponse'];
export type InstrumentAliasInput = components['schemas']['InstrumentAliasInput'];
export type ManualInstrumentCreateRequest = components['schemas']['ManualInstrumentCreateRequest'];
export type ManualInstrumentUpdateRequest = components['schemas']['ManualInstrumentUpdateRequest'];
export type InstrumentType = components['schemas']['InstrumentSummaryResponse']['instrumentType'];
export type ValuationMethod = components['schemas']['InstrumentSummaryResponse']['valuationMethod'];
export type AliasType = components['schemas']['InstrumentAliasResponse']['type'];
export type SliceResponseInstrumentSummary = components['schemas']['SliceResponseInstrumentSummaryResponse'];
