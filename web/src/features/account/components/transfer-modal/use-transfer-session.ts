import { notifications } from '@mantine/notifications';
import { useForm, useStore } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import type { ActivityResponse, FinancialAccount } from '../../types';
import { POSITIVE_DECIMAL_REGEX } from '../../utils/account-formatters';
import { createTransferFormDefaults, getDestinationAccounts, resolveEffectiveAt, resolveInitialTransferAccounts } from './transfer-domain';
import { invalidateTransferRelatedQueries, notifyCommitError, notifyPreviewError } from './transfer-errors';
import type { TransferFormValues, TransferState } from './transfer-types';

export interface UseTransferSessionOptions {
  accounts: FinancialAccount[];
  defaultSourceAccountId?: string;
  defaultDestinationAccountId?: string;
  lockSourceAccount?: boolean;
  onClose: () => void;
  onSuccess?: (activity: ActivityResponse) => void;
}

export function useTransferSession(options: UseTransferSessionOptions) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<TransferState>({ step: 'edit' });

  const accountsById = useMemo(() => new Map(options.accounts.map((a) => [a.id, a])), [options.accounts]);

  const initialAccountIds = useMemo(
    () =>
      resolveInitialTransferAccounts({
        accounts: options.accounts,
        defaultSourceAccountId: options.defaultSourceAccountId,
        defaultDestinationAccountId: options.defaultDestinationAccountId,
        lockSourceAccount: options.lockSourceAccount
      }),
    [options.accounts, options.defaultSourceAccountId, options.defaultDestinationAccountId, options.lockSourceAccount]
  );

  const form = useForm({
    defaultValues: createTransferFormDefaults(initialAccountIds),
    onSubmit: async ({ value }) => {
      executePreview(value, { refreshEffectiveAt: true });
    }
  });

  const formValues = useStore(form.store, (s) => s.values);

  const previewMutation = $api.useMutation('post', '/api/v1/transfers/previews', {
    onError: notifyPreviewError
  });

  const commitMutation = $api.useMutation('post', '/api/v1/transfers', {
    onSuccess: (activity) => {
      invalidateTransferRelatedQueries(queryClient);
      notifications.show({
        title: 'Transfer Executed',
        message: 'Your transfer has been completed and recorded in the activity history.',
        color: 'teal'
      });

      if (state.step === 'preview') {
        setState({
          step: 'success',
          activity,
          preview: state.session.preview
        });
      }

      options.onSuccess?.(activity);
    },
    onError: notifyCommitError
  });

  function executePreview(
    values: TransferFormValues,
    previewOptions?: {
      confirmPolicyBreach?: boolean;
      refreshEffectiveAt?: boolean;
      clientRequestId?: string;
    }
  ) {
    const confirmBreach = previewOptions?.confirmPolicyBreach ?? values.confirmPolicyBreach;
    const effectiveAt =
      !previewOptions?.refreshEffectiveAt && state.step === 'preview' ? state.session.requestBody.effectiveAt : resolveEffectiveAt(values);

    const clientRequestId = previewOptions?.clientRequestId ?? crypto.randomUUID();

    const requestBody = {
      sourceAccountId: values.sourceAccountId,
      destinationAccountId: values.destinationAccountId,
      amount: values.amount.trim(),
      recordingMode: values.recordingMode,
      effectiveAt,
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

  function commitTransfer() {
    if (state.step !== 'preview') return;
    const { preview, requestBody, clientRequestId } = state.session;

    commitMutation.mutate({
      body: {
        clientRequestId,
        sourceAccountId: preview.sourceAccountId,
        destinationAccountId: preview.destinationAccountId,
        amount: preview.amount,
        recordingMode: requestBody.recordingMode,
        effectiveAt: requestBody.effectiveAt,
        confirmPolicyBreach: requestBody.confirmPolicyBreach,
        expectedSourceBalanceVersion: preview.sourceVersion,
        expectedDestinationBalanceVersion: preview.destinationVersion
      }
    });
  }

  function repreviewWithPolicyBreach(confirm: boolean) {
    form.setFieldValue('confirmPolicyBreach', confirm);
    executePreview(form.state.values, {
      confirmPolicyBreach: confirm,
      refreshEffectiveAt: false,
      clientRequestId: state.step === 'preview' ? state.session.clientRequestId : undefined
    });
  }

  function reloadAndRepreview() {
    commitMutation.reset();
    executePreview(form.state.values, {
      refreshEffectiveAt: true,
      clientRequestId: crypto.randomUUID()
    });
  }

  function editTransfer() {
    previewMutation.reset();
    commitMutation.reset();
    setState({ step: 'edit' });
  }

  function swapAccounts() {
    if (options.lockSourceAccount) return;
    const currentSource = form.getFieldValue('sourceAccountId');
    const currentDest = form.getFieldValue('destinationAccountId');
    if (!currentSource || !currentDest) return;
    form.setFieldValue('sourceAccountId', currentDest);
    form.setFieldValue('destinationAccountId', currentSource);
    editTransfer();
  }

  const sourceAccount = accountsById.get(formValues.sourceAccountId);
  const destinationAccount = accountsById.get(formValues.destinationAccountId);
  const destinationAccounts = getDestinationAccounts(options.accounts, formValues.sourceAccountId);

  const sourceBalanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: formValues.sourceAccountId } }
    },
    {
      enabled: Boolean(formValues.sourceAccountId)
    }
  );

  const isAmountValid =
    Boolean(formValues.amount?.trim()) &&
    POSITIVE_DECIMAL_REGEX.test(formValues.amount.trim()) &&
    Number.parseFloat(formValues.amount.trim()) > 0;

  const canPreview =
    Boolean(formValues.sourceAccountId) &&
    Boolean(formValues.destinationAccountId) &&
    formValues.sourceAccountId !== formValues.destinationAccountId &&
    isAmountValid &&
    !previewMutation.isPending;

  const isCommitConflict =
    commitMutation.isError &&
    (normalizeError(commitMutation.error).status === 409 || normalizeError(commitMutation.error).code === 'BALANCE_VERSION_CONFLICT');

  const previewError = previewMutation.isError ? normalizeError(previewMutation.error) : null;

  return {
    state,
    accounts: options.accounts,
    form,
    formValues,
    sourceAccount,
    destinationAccount,
    destinationAccounts,
    sourceBalanceQuery,
    isAmountValid,
    canPreview,
    isCommitConflict,
    previewError,
    previewMutation,
    commitMutation,
    previewTransfer: () => {
      void form.handleSubmit();
    },
    commitTransfer,
    editTransfer,
    swapAccounts,
    repreviewWithPolicyBreach,
    reloadAndRepreview
  };
}

export type TransferSessionResult = ReturnType<typeof useTransferSession>;
