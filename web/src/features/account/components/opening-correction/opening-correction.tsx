import { Alert, Badge, Button, Group, Skeleton, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  ArrowClockwiseIcon,
  CalendarBlankIcon,
  CheckCircleIcon,
  CurrencyCircleDollarIcon,
  PencilSimpleIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import type { FinancialAccount } from '../../types';
import { formatCurrency, formatDateTime, PLAIN_DECIMAL_REGEX } from '../../utils/account-formatters';
import classes from './opening-correction.module.css';

interface OpeningCorrectionOverlayProps {
  accountId: string;
  currentOpeningBalance?: string | null;
}

interface OpeningCorrectionFormProps {
  account: FinancialAccount;
  currentOpeningBalance?: string | null;
  onRefetchAccount: () => Promise<unknown>;
}

export function OpeningCorrection({ accountId, currentOpeningBalance }: OpeningCorrectionOverlayProps) {
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={36} radius="sm" />
        <Skeleton height={82} radius="md" />
        <Skeleton height={96} radius="md" />
      </Stack>
    );
  }

  if (accountQuery.isError || !accountQuery.data) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account" color="red" variant="light" m="md">
        <Text size="sm">The requested financial account could not be loaded.</Text>
      </Alert>
    );
  }

  return (
    <OpeningCorrectionForm
      key={`${accountQuery.data.id}-${accountQuery.data.version ?? 0}`}
      account={accountQuery.data}
      currentOpeningBalance={currentOpeningBalance}
      onRefetchAccount={accountQuery.refetch}
    />
  );
}

function OpeningCorrectionForm({ account, currentOpeningBalance, onRefetchAccount }: OpeningCorrectionFormProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();

  const correctionMutation = $api.useMutation('put', '/api/v1/accounts/{accountId}/opening-state', {
    onError: (err) => {
      const apiErr = normalizeError(err);
      if (apiErr.status === 409 || apiErr.code === 'ACCOUNT_VERSION_CONFLICT' || apiErr.code === 'OPENING_STATE_CONFLICT') {
        notifications.show({
          title: 'Conflict Detected (HTTP 409)',
          message: 'The account or opening state was modified by another session. Please reload latest server data.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else {
        notifications.show({
          title: 'Opening Correction Failed',
          message: apiErr.message || 'Could not correct opening state. Please check your inputs.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      }
    }
  });

  const form = useForm({
    defaultValues: {
      amount: currentOpeningBalance ?? '0.00',
      correctionReason: ''
    },
    onSubmit: async ({ value }) => {
      if (!account.coverageFrom) {
        notifications.show({
          title: 'Opening State Required',
          message: 'Cannot perform opening correction on an account without an established opening date.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
        return;
      }
      const effectiveAt = account.coverageFrom;
      const currentVer = account.version ?? 0;

      correctionMutation.mutate(
        {
          params: { path: { accountId: account.id } },
          body: {
            clientRequestId: crypto.randomUUID(),
            amount: value.amount.trim(),
            effectiveAt,
            correctionReason: value.correctionReason.trim(),
            version: currentVer
          }
        },
        {
          onSuccess: (data) => {
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
            queryClient.invalidateQueries({
              queryKey: ['get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }]
            });
            queryClient.invalidateQueries({
              queryKey: ['get', '/api/v1/accounts/{accountId}/balance']
            });
            queryClient.invalidateQueries({
              queryKey: ['get', '/api/v1/activities']
            });
            notifications.show({
              title: 'Opening State Corrected',
              message: `Account "${data.name}" opening balance corrected to ${formatCurrency(value.amount.trim(), account.currency)}.`,
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });
            current.complete();
          }
        }
      );
    }
  });

  const correctionError = correctionMutation.isError ? normalizeError(correctionMutation.error) : null;
  const isConflict =
    correctionError?.status === 409 ||
    correctionError?.code === 'ACCOUNT_VERSION_CONFLICT' ||
    correctionError?.code === 'OPENING_STATE_CONFLICT';

  async function handleReloadLatest() {
    correctionMutation.reset();
    await onRefetchAccount();
    notifications.show({
      title: 'Refreshed',
      message: 'Latest account state reloaded from server.',
      color: 'teal'
    });
  }

  function handleClose() {
    correctionMutation.reset();
    form.reset();
    current.dismiss('cancelled');
  }

  return (
    <>
      <Group justify="flex-end" mb="md">
        <Badge variant="outline" color="gray" className={classes.versionBadge}>
          Version: {account.version ?? 0}
        </Badge>
      </Group>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
        className={classes.form}
        noValidate>
        <Stack gap="md">
          {/* Concurrency Conflict Alert */}
          {isConflict && (
            <Alert
              icon={<WarningCircleIcon size={20} weight="bold" />}
              title="Account Updated in Another Session"
              color="orange"
              variant="filled"
              className={classes.conflictBox}>
              <Text size="sm">
                This account was updated on the server while this form was open. Please reload the latest state before saving.
              </Text>
              <Button
                size="sm"
                variant="white"
                color="dark"
                mt="xs"
                className={classes.reloadBtn}
                onClick={handleReloadLatest}
                leftSection={<ArrowClockwiseIcon size={14} weight="bold" />}>
                Reload Latest State
              </Button>
            </Alert>
          )}

          {/* General API Error Feedback */}
          {!isConflict && correctionError && (
            <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Correction Failed" color="red" variant="light">
              <Text size="sm">
                {correctionError.fieldErrors?.length
                  ? correctionError.fieldErrors.map((f) => f.detail).join('; ')
                  : correctionError.message || 'Could not correct opening state.'}
              </Text>
            </Alert>
          )}

          {!account.coverageFrom && (
            <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Opening State Unavailable" color="yellow" variant="light">
              <Text size="sm">This account does not have a recorded opening balance or start date.</Text>
            </Alert>
          )}

          {/* Historical Anchor Effective Date */}
          <div className={classes.anchorCard}>
            <span className={classes.anchorLabel}>
              <CalendarBlankIcon size={16} weight="bold" />
              Account Start Date
            </span>
            <span className={classes.anchorValue}>{formatDateTime(account.coverageFrom) || 'No Established Date'}</span>
            <p className={classes.anchorHelp}>
              This correction adjusts the balance on this starting date. The date itself cannot be changed to keep your history accurate.
            </p>
          </div>

          {/* Current vs New Amount Comparison */}
          {currentOpeningBalance !== undefined && currentOpeningBalance !== null && (
            <Group justify="space-between" align="center" px="xs">
              <Text size="xs" c="dimmed">
                Current Opening Balance:
              </Text>
              <Text size="xs" fw={600}>
                {formatCurrency(currentOpeningBalance, account.currency)}
              </Text>
            </Group>
          )}

          {/* Corrected Amount Field */}
          <form.Field
            name="amount"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Corrected opening balance amount is required.';
                if (!PLAIN_DECIMAL_REGEX.test(trimmed)) {
                  return 'Must be an exact decimal amount (e.g. 0.00, 1000.00).';
                }
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Corrected Opening Balance"
                placeholder="0.00"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                leftSection={<CurrencyCircleDollarIcon size={18} />}
                rightSection={
                  <Badge variant="light" color="teal" size="sm" mr={6}>
                    {account.currency}
                  </Badge>
                }
                required
                autoFocus
              />
            )}
          </form.Field>

          {/* Correction Reason */}
          <form.Field
            name="correctionReason"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Correction audit reason is required.';
                if (trimmed.length > 500) return 'Reason must not exceed 500 characters.';
                return undefined;
              }
            }}>
            {(field) => (
              <Textarea
                label="Reason for Correction"
                placeholder="e.g. Corrected bank statement balance or initial cash amount"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                minRows={3}
                maxRows={5}
                autosize
                required
              />
            )}
          </form.Field>

          {/* Action Buttons */}
          <div className={classes.actions}>
            <Button variant="default" onClick={handleClose} disabled={correctionMutation.isPending} className={classes.actionBtn}>
              Cancel
            </Button>
            <Button
              type="submit"
              color="brand"
              loading={correctionMutation.isPending}
              disabled={!account.coverageFrom}
              className={classes.actionBtn}
              leftSection={<PencilSimpleIcon size={16} weight="bold" />}>
              Save Correction
            </Button>
          </div>
        </Stack>
      </form>
    </>
  );
}

export const OpeningCorrectionOverlay = registerOverlay(OpeningCorrection, {
  name: 'opening-correction',
  title: (
    <Group gap="xs">
      <PencilSimpleIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={600}>Correct Opening State</Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'md'
});
