import { Alert, Badge, Button, Divider, Group, Select, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ArrowClockwiseIcon, CheckCircleIcon, GearIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm, useStore } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import type { FinancialAccount, NegativeBalancePolicy } from '../../types';
import { COMMON_TIMEZONES, getPolicyDescription, isAssetKind, isLiabilityKind } from '../../utils/account-formatters';
import classes from './account-settings.module.css';

interface AccountSettingsOverlayProps {
  accountId: string;
}

interface AccountSettingsFormProps {
  account: FinancialAccount;
  onRefetchAccount: () => Promise<unknown>;
}

export function AccountSettings({ accountId }: AccountSettingsOverlayProps) {
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={36} radius="sm" />
        <Skeleton height={48} radius="sm" />
        <Skeleton height={48} radius="sm" />
        <Skeleton height={96} radius="md" />
      </Stack>
    );
  }

  if (accountQuery.isError || !accountQuery.data) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account settings" color="red" variant="light" m="md">
        <Text size="sm">The requested financial account could not be loaded.</Text>
      </Alert>
    );
  }

  return (
    <AccountSettingsForm
      key={`${accountQuery.data.id}-${accountQuery.data.version ?? 0}`}
      account={accountQuery.data}
      onRefetchAccount={accountQuery.refetch}
    />
  );
}

function AccountSettingsForm({ account, onRefetchAccount }: AccountSettingsFormProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();

  const canConfigurePolicy = account.trackingMode === 'FULL_LEDGER' && isAssetKind(account.kind) && !isLiabilityKind(account.kind);

  const metadataMutation = $api.useMutation('put', '/api/v1/accounts/{accountId}', {
    onError: (err) => {
      const apiErr = normalizeError(err);
      if (apiErr.status === 409 || apiErr.code === 'ACCOUNT_VERSION_CONFLICT') {
        notifications.show({
          title: 'Version Conflict (HTTP 409)',
          message: 'The account was modified by another session. Please reload latest server data.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else {
        notifications.show({
          title: 'Update Failed',
          message: apiErr.message || 'Could not update account settings.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      }
    }
  });

  const policyMutation = $api.useMutation('put', '/api/v1/accounts/{accountId}/policy', {
    onError: (err) => {
      const apiErr = normalizeError(err);
      if (apiErr.status === 409 || apiErr.code === 'ACCOUNT_VERSION_CONFLICT') {
        notifications.show({
          title: 'Version Conflict (HTTP 409)',
          message: 'The account was modified by another session. Please reload latest server data.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else {
        notifications.show({
          title: 'Policy Update Failed',
          message: apiErr.message || 'Could not update account policy.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      }
    }
  });

  const form = useForm({
    defaultValues: {
      name: account.name,
      timeZone: account.timeZone,
      policy: (account.policy ?? 'HARD_FLOOR') as NegativeBalancePolicy,
      authorizedLimit: account.authorizedLimit ?? ''
    },
    onSubmit: async ({ value }) => {
      const currentVer = account.version ?? 0;

      const metadataChanged = value.name.trim() !== account.name || value.timeZone.trim() !== account.timeZone;
      const policyChanged =
        canConfigurePolicy &&
        (value.policy !== account.policy ||
          (value.policy === 'AUTHORIZED_LIMIT' && value.authorizedLimit.trim() !== (account.authorizedLimit || '')));

      if (!metadataChanged && !policyChanged) {
        notifications.show({
          title: 'No Changes',
          message: 'No changes were detected in the form.',
          color: 'blue'
        });
        handleClose();
        return;
      }

      if (metadataChanged && policyChanged) {
        metadataMutation.mutate(
          {
            params: { path: { accountId: account.id } },
            body: {
              clientRequestId: crypto.randomUUID(),
              name: value.name.trim(),
              timeZone: value.timeZone.trim(),
              version: currentVer
            }
          },
          {
            onSuccess: (updated) => {
              policyMutation.mutate(
                {
                  params: { path: { accountId: account.id } },
                  body: {
                    clientRequestId: crypto.randomUUID(),
                    policy: value.policy,
                    authorizedLimit:
                      account.kind === 'CASH_CURRENT' && value.policy === 'AUTHORIZED_LIMIT' ? value.authorizedLimit.trim() : undefined,
                    version: updated.version ?? currentVer + 1
                  }
                },
                {
                  onSuccess: () => {
                    queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
                    queryClient.invalidateQueries({
                      queryKey: ['get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }]
                    });
                    notifications.show({
                      title: 'Settings & Policy Updated',
                      message: `Account "${updated.name}" updated successfully.`,
                      color: 'teal',
                      icon: <CheckCircleIcon size={18} weight="bold" />
                    });
                    handleClose();
                  }
                }
              );
            }
          }
        );
      } else if (metadataChanged) {
        metadataMutation.mutate(
          {
            params: { path: { accountId: account.id } },
            body: {
              clientRequestId: crypto.randomUUID(),
              name: value.name.trim(),
              timeZone: value.timeZone.trim(),
              version: currentVer
            }
          },
          {
            onSuccess: (data) => {
              queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
              queryClient.invalidateQueries({
                queryKey: ['get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }]
              });
              notifications.show({
                title: 'Settings Updated',
                message: `Account "${data.name}" metadata saved successfully.`,
                color: 'teal',
                icon: <CheckCircleIcon size={18} weight="bold" />
              });
              handleClose();
            }
          }
        );
      } else if (policyChanged) {
        policyMutation.mutate(
          {
            params: { path: { accountId: account.id } },
            body: {
              clientRequestId: crypto.randomUUID(),
              policy: value.policy,
              authorizedLimit:
                account.kind === 'CASH_CURRENT' && value.policy === 'AUTHORIZED_LIMIT' ? value.authorizedLimit.trim() : undefined,
              version: currentVer
            }
          },
          {
            onSuccess: () => {
              queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
              queryClient.invalidateQueries({
                queryKey: ['get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }]
              });
              notifications.show({
                title: 'Policy Updated',
                message: 'Negative balance policy saved successfully.',
                color: 'teal',
                icon: <CheckCircleIcon size={18} weight="bold" />
              });
              handleClose();
            }
          }
        );
      }
    }
  });

  const currentPolicy = useStore(form.store, (state) => state.values.policy);

  const metadataError = metadataMutation.isError ? normalizeError(metadataMutation.error) : null;
  const policyError = policyMutation.isError ? normalizeError(policyMutation.error) : null;
  const activeError = metadataError ?? policyError;
  const isConflict =
    activeError?.status === 409 || activeError?.code === 'ACCOUNT_VERSION_CONFLICT' || activeError?.code === 'BALANCE_VERSION_CONFLICT';

  async function handleReloadLatest() {
    metadataMutation.reset();
    policyMutation.reset();
    await onRefetchAccount();
    notifications.show({
      title: 'Refreshed',
      message: 'Latest account state loaded from server.',
      color: 'teal'
    });
  }

  function handleClose() {
    metadataMutation.reset();
    policyMutation.reset();
    form.reset();
    current.dismiss('cancelled');
  }

  const isSaving = metadataMutation.isPending || policyMutation.isPending;

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
          {/* Optimistic Concurrency Conflict Alert */}
          {isConflict && (
            <Alert
              icon={<WarningCircleIcon size={20} weight="bold" />}
              title="Version Conflict Detected (409 Conflict)"
              color="orange"
              variant="filled"
              className={classes.conflictBox}>
              <Text size="sm">
                This account was modified on the server (current version: {account.version ?? 0}) since you opened this dialog. Your changes
                cannot overwrite newer revisions.
              </Text>
              <Button
                size="xs"
                variant="white"
                color="dark"
                mt="xs"
                onClick={handleReloadLatest}
                leftSection={<ArrowClockwiseIcon size={14} weight="bold" />}>
                Reload Latest Server State
              </Button>
            </Alert>
          )}

          {/* General API Error Feedback */}
          {!isConflict && activeError && (
            <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Save Failed" color="red" variant="light">
              <Text size="sm">
                {activeError.fieldErrors?.length
                  ? activeError.fieldErrors.map((f) => f.detail).join('; ')
                  : activeError.message || 'Could not save account changes.'}
              </Text>
            </Alert>
          )}

          {/* General Metadata */}
          <div>
            <Text className={classes.sectionTitle}>General Identification</Text>
            <Text size="xs" c="dimmed">
              Update display name and regional IANA timezone.
            </Text>
          </div>

          <form.Field
            name="name"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Account name is required.';
                if (trimmed.length > 160) return 'Account name must not exceed 160 characters.';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Account Name"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                required
              />
            )}
          </form.Field>

          <form.Field
            name="timeZone"
            validators={{
              onChange: ({ value }) => (!value ? 'Time zone is required.' : undefined)
            }}>
            {(field) => (
              <Select
                label="Time Zone (IANA)"
                searchable
                value={field.state.value}
                onChange={(val) => field.handleChange(val || 'UTC')}
                data={Array.from(new Set([account.timeZone, ...COMMON_TIMEZONES])).map((tz) => ({
                  value: tz,
                  label: tz
                }))}
                required
              />
            )}
          </form.Field>

          {/* Cash Policy Section */}
          {canConfigurePolicy && (
            <>
              <Divider my="xs" />
              <div>
                <Text className={classes.sectionTitle}>Negative Balance Policy</Text>
                <Text size="xs" c="dimmed">
                  Manage overdraft boundaries and negative cash balance rules.
                </Text>
              </div>

              <form.Field name="policy">
                {(field) => (
                  <Select
                    label="Policy Enforcement"
                    description={getPolicyDescription(field.state.value)}
                    value={field.state.value}
                    onChange={(val) => {
                      if (!val) return;
                      field.handleChange(val as NegativeBalancePolicy);
                    }}
                    data={[
                      { value: 'HARD_FLOOR', label: 'Hard Floor (No Overdraft)' },
                      { value: 'SOFT_FLOOR', label: 'Soft Floor (Warn on Overdraft)' },
                      { value: 'TRACK_REALITY', label: 'Track Reality (No Limits)' },
                      {
                        value: 'AUTHORIZED_LIMIT',
                        label: 'Authorized Overdraft Limit',
                        disabled: account.kind !== 'CASH_CURRENT'
                      }
                    ]}
                    required
                  />
                )}
              </form.Field>

              {account.kind === 'CASH_CURRENT' && currentPolicy === 'AUTHORIZED_LIMIT' && (
                <form.Field
                  name="authorizedLimit"
                  validators={{
                    onChange: ({ value }) => {
                      const num = Number.parseFloat(value);
                      if (!value.trim() || Number.isNaN(num) || num <= 0) {
                        return 'Authorized limit must be a positive number.';
                      }
                      return undefined;
                    }
                  }}>
                  {(field) => (
                    <TextInput
                      label="Authorized Overdraft Limit"
                      placeholder="e.g. 1000.00"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.currentTarget.value)}
                      onBlur={field.handleBlur}
                      error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                      required
                    />
                  )}
                </form.Field>
              )}
            </>
          )}

          {/* Action Buttons */}
          <div className={classes.actions}>
            <Button variant="default" onClick={handleClose} disabled={isSaving}>
              Cancel
            </Button>
            <Button type="submit" color="brand" loading={isSaving}>
              Save Changes
            </Button>
          </div>
        </Stack>
      </form>
    </>
  );
}

export const AccountSettingsOverlay = registerOverlay(AccountSettings, {
  name: 'account-settings',
  title: (
    <Group gap="xs">
      <GearIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={600}>Account Settings &amp; Policies</Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'md'
});
