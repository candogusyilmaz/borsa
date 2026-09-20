import { notifications } from '@mantine/notifications';
import { useForm, useStore } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { toDatetimeLocal } from '@/features/account/utils/account-formatters';
import type { TradeFormValues, TradeSide } from '../../types';
import type { RecordTradeProps, TradeState } from './trade-types';

export function useTradeSession(options: RecordTradeProps) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<TradeState>({ step: 'edit' });

  const form = useForm({
    defaultValues: {
      accountId: options.defaultAccountId ?? '',
      instrumentId: options.defaultInstrumentId ?? '',
      side: (options.defaultSide ?? 'BUY') as TradeSide,
      quantity: '',
      unitPrice: '',
      commissionAmount: '0',
      recordingMode: 'CURRENT_ACTION' as 'CURRENT_ACTION' | 'HISTORICAL_FACT',
      effectiveAt: toDatetimeLocal(new Date()),
      economicSequence: 0,
      confirmPolicyBreach: false
    },
    onSubmit: async ({ value }) => {
      executePreview(value, { refreshEffectiveAt: true });
    }
  });

  const formValues = useStore(form.store, (s) => s.values);

  const previewMutation = $api.useMutation('post', '/api/v1/trades/previews', {
    onError: (err) => {
      showApiError(err, {
        title: 'Preview Failed',
        fallbackMessage: 'Could not calculate trade preview. Please check your inputs.'
      });
    }
  });

  const commitMutation = $api.useMutation('post', '/api/v1/trades', {
    onSuccess: async (trade) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/trades'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/investing/positions'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/reconciliations'] })
      ]);

      notifications.show({
        title: 'Trade Executed',
        message: `${trade.side === 'BUY' ? 'Bought' : 'Sold'} ${trade.quantity} ${trade.instrumentSymbol} successfully.`,
        color: 'teal'
      });

      if (state.step === 'preview') {
        setState({
          step: 'success',
          trade,
          preview: state.session.preview
        });
      }
    },
    onError: (err) => {
      showApiError(err, {
        title: 'Trade Execution Failed',
        fallbackMessage: 'Could not execute trade. The balance or position may have changed.'
      });
    }
  });

  function executePreview(
    values: TradeFormValues,
    previewOptions?: {
      confirmPolicyBreach?: boolean;
      refreshEffectiveAt?: boolean;
      clientRequestId?: string;
    }
  ) {
    const confirmBreach = previewOptions?.confirmPolicyBreach ?? values.confirmPolicyBreach;
    const effectiveAt =
      values.recordingMode === 'HISTORICAL_FACT'
        ? new Date(values.effectiveAt).toISOString()
        : previewOptions?.refreshEffectiveAt || state.step !== 'preview'
          ? new Date().toISOString()
          : state.session.requestBody.effectiveAt;

    const clientRequestId = previewOptions?.clientRequestId ?? crypto.randomUUID();

    const requestBody = {
      accountId: values.accountId,
      instrumentId: values.instrumentId,
      side: values.side,
      quantity: values.quantity.trim(),
      unitPrice: values.unitPrice.trim(),
      commissionAmount: values.commissionAmount.trim() || '0',
      recordingMode: values.recordingMode,
      effectiveAt,
      economicSequence: Number(values.economicSequence) || 0,
      confirmPolicyBreach: confirmBreach
    };

    previewMutation.mutate(
      { body: requestBody },
      {
        onSuccess: (preview) => {
          setState({
            step: 'preview',
            session: {
              preview,
              requestBody,
              clientRequestId
            }
          });
        }
      }
    );
  }

  function commitTrade() {
    if (state.step !== 'preview') return;
    const { session } = state;

    commitMutation.mutate({
      body: {
        ...session.requestBody,
        clientRequestId: session.clientRequestId,
        expectedCashBalanceVersion: session.preview.cashBalanceVersion,
        expectedPositionVersion: session.preview.positionVersion
      }
    });
  }

  function backToEdit() {
    setState({ step: 'edit' });
  }

  function resetSession() {
    setState({ step: 'edit' });
    form.reset();
  }

  return {
    state,
    form,
    formValues,
    executePreview,
    commitTrade,
    backToEdit,
    resetSession,
    isPreviewLoading: previewMutation.isPending,
    isCommitLoading: commitMutation.isPending
  };
}
