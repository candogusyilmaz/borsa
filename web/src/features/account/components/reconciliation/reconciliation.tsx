import { Alert, Group, Skeleton, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { CheckCircleIcon, SlidersIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { registerOverlay } from '@/shared/overlay';
import { ReconciliationCorrection } from './reconciliation-correction';
import { ReconciliationDetail } from './reconciliation-detail';
import { ReconciliationForm } from './reconciliation-form';
import { ReconciliationList } from './reconciliation-list';
import { ReconciliationPreview } from './reconciliation-preview';
import type {
  ReconciliationAction,
  ReconciliationPreviewRequest,
  ReconciliationPreviewResponse,
  ReconciliationResponse
} from './reconciliation-types';

export interface ReconciliationOverlayProps {
  accountId: string;
  initialReconciliationId?: string;
  initialMode?: 'list' | 'create';
}

export function Reconciliation({ accountId, initialReconciliationId, initialMode = 'list' }: ReconciliationOverlayProps) {
  const queryClient = useQueryClient();

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const [mode, setMode] = useState<'list' | 'create' | 'preview' | 'detail' | 'correct'>(initialReconciliationId ? 'detail' : initialMode);
  const [selectedRecId, setSelectedRecId] = useState<string | null>(initialReconciliationId || null);
  const [targetRecForCorrection, setTargetRecForCorrection] = useState<ReconciliationResponse | null>(null);
  const [previewData, setPreviewData] = useState<ReconciliationPreviewResponse | null>(null);

  const previewMutation = $api.useMutation('post', '/api/v1/accounts/{accountId}/reconciliation-previews', {
    onError: (err) => {
      showApiError(err, {
        title: 'Preview Failed',
        fallbackMessage: 'Could not preview reconciliation.'
      });
    }
  });

  const commitMutation = $api.useMutation('post', '/api/v1/accounts/{accountId}/reconciliations', {
    onError: (err) => {
      showApiError(err, {
        title: 'Commit Failed',
        fallbackMessage: 'Could not commit reconciliation.'
      });
    }
  });

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}'] });
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] });
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/reconciliations'] });
    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] });
  }

  function handlePreview(request: ReconciliationPreviewRequest) {
    previewMutation.mutate(
      {
        params: { path: { accountId } },
        body: request
      },
      {
        onSuccess: (data) => {
          setPreviewData(data);
          setMode('preview');
        }
      }
    );
  }

  function handleCommit(resolution: ReconciliationAction, adjustmentReason?: string) {
    if (!previewData) return;

    commitMutation.mutate(
      {
        params: { path: { accountId } },
        body: {
          clientRequestId: crypto.randomUUID(),
          statementReference: previewData.statementReference,
          statementOpeningAt: previewData.statementOpeningAt,
          statementClosingAt: previewData.statementClosingAt,
          statementOpeningBalance: previewData.statementOpeningBalance,
          statementClosingBalance: previewData.statementClosingBalance,
          expectedBalanceVersion: previewData.projectionVersion ?? 0,
          resolution,
          adjustmentReason: resolution === 'CREATE_ADJUSTMENT' && adjustmentReason?.trim() ? adjustmentReason.trim() : undefined
        }
      },
      {
        onSuccess: (rec) => {
          invalidateQueries();
          notifications.show({
            title: 'Reconciliation Recorded',
            message: `Statement "${rec.statementReference}" successfully reconciled and committed.`,
            color: 'teal',
            icon: <CheckCircleIcon size={18} weight="bold" />
          });
          setSelectedRecId(rec.id);
          setMode('detail');
        }
      }
    );
  }

  const account = accountQuery.data;

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={40} radius="sm" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={120} radius="md" />
      </Stack>
    );
  }

  if (accountQuery.isError || !account) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account" color="red" variant="light" m="md">
        <Text size="sm">The requested financial account could not be loaded.</Text>
      </Alert>
    );
  }

  return (
    <div>
      {mode === 'list' && (
        <ReconciliationList
          account={account}
          onStartNew={() => setMode('create')}
          onSelectReconciliation={(recId) => {
            setSelectedRecId(recId);
            setMode('detail');
          }}
        />
      )}

      {mode === 'create' && (
        <ReconciliationForm
          account={account}
          isLoading={previewMutation.isPending}
          onPreview={handlePreview}
          onCancel={() => setMode('list')}
        />
      )}

      {mode === 'preview' && previewData && (
        <ReconciliationPreview
          account={account}
          preview={previewData}
          isPendingCommit={commitMutation.isPending}
          commitError={commitMutation.error}
          onCommit={handleCommit}
          onEdit={() => setMode('create')}
          onReload={() => {
            handlePreview({
              statementReference: previewData.statementReference,
              statementOpeningAt: previewData.statementOpeningAt,
              statementClosingAt: previewData.statementClosingAt,
              statementOpeningBalance: previewData.statementOpeningBalance,
              statementClosingBalance: previewData.statementClosingBalance
            });
          }}
        />
      )}

      {mode === 'detail' && selectedRecId && (
        <ReconciliationDetail
          reconciliationId={selectedRecId}
          isAccountArchived={account.archived}
          onBack={() => setMode('list')}
          onStartCorrection={async () => {
            const res = await queryClient.fetchQuery(
              $api.queryOptions('get', '/api/v1/reconciliations/{reconciliationId}', {
                params: { path: { reconciliationId: selectedRecId } }
              })
            );
            setTargetRecForCorrection(res);
            setMode('correct');
          }}
        />
      )}

      {mode === 'correct' && targetRecForCorrection && (
        <ReconciliationCorrection
          account={account}
          targetReconciliation={targetRecForCorrection}
          onSuccess={(newRec) => {
            invalidateQueries();
            notifications.show({
              title: 'Reconciliation Corrected',
              message: `Reconciliation was successfully corrected and previous record superseded.`,
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });
            setSelectedRecId(newRec.id);
            setMode('detail');
          }}
          onCancel={() => setMode('detail')}
        />
      )}
    </div>
  );
}

export const ReconciliationOverlay = registerOverlay(Reconciliation, {
  name: 'reconciliation',
  title: (
    <Group gap="xs">
      <SlidersIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Statement Reconciliation
      </Text>
    </Group>
  ),
  presentation: 'drawer',
  size: 'lg'
});
