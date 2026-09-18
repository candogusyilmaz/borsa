import type { components } from '@/api/schema';

export type ReconciliationResponse = components['schemas']['ReconciliationResponse'];
export type ReconciliationPreviewResponse = components['schemas']['ReconciliationPreviewResponse'];
export type ReconciliationCommitRequest = components['schemas']['ReconciliationCommitRequest'];
export type ReconciliationCorrectionRequest = components['schemas']['ReconciliationCorrectionRequest'];
export type ReconciliationPreviewRequest = components['schemas']['ReconciliationPreviewRequest'];
export type ReconciliationAction = components['schemas']['ReconciliationCommitRequest']['resolution'];
export type ReconciliationLifecycleStatus = components['schemas']['ReconciliationResponse']['lifecycleStatus'];
export type ReconciliationResolution = components['schemas']['ReconciliationResponse']['resolution'];
