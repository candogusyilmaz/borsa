import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import type { FinancialAccount } from '../../types';
import { AccountActionsDrawer } from '../account-actions-drawer';
import { AccountActivitiesDrawer } from '../account-activities-drawer';
import { AccountInfoDrawer } from '../account-info-drawer';
import { AccountSettingsModal } from '../account-settings-modal/account-settings-modal';
import { ArchiveAccountModal } from '../archive-account-modal/archive-account-modal';
import { OpeningCorrectionModal } from '../opening-correction-modal/opening-correction-modal';
import { TransferModal } from '../transfer-modal/transfer-modal';
import { useAccountDetailOverlay } from './account-detail-overlay-provider';

interface AccountDetailOverlayHostProps {
  account: FinancialAccount;
  refetchAccounts: () => Promise<unknown>;
  onAccountArchived: () => Promise<void>;
}

export function AccountDetailOverlayHost({ account, refetchAccounts, onAccountArchived }: AccountDetailOverlayHostProps) {
  const { active, close } = useAccountDetailOverlay();
  const queryClient = useQueryClient();

  async function refreshSelectedAccount() {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }).queryKey
      }),
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}/balance', {
          params: { path: { accountId: account.id } }
        }).queryKey
      }),
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/activities', { params: { query: { accountId: account.id, pageable: {} } } }).queryKey
      }),
      refetchAccounts()
    ]);
  }

  return (
    <>
      <TransferModal
        opened={active?.type === 'transfer'}
        onClose={close}
        defaultSourceAccountId={account.id}
        onSuccess={refreshSelectedAccount}
      />

      <AccountActionsDrawer account={account} opened={active?.type === 'actions'} onClose={close} onRefresh={refreshSelectedAccount} />

      <AccountInfoDrawer account={account} opened={active?.type === 'info'} onClose={close} />

      <AccountSettingsModal
        key={`settings-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={active?.type === 'settings'}
        onClose={close}
        onRefetchAccount={refreshSelectedAccount}
      />

      <ArchiveAccountModal
        key={`archive-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={active?.type === 'archive'}
        onClose={close}
        onRefetchAccount={refreshSelectedAccount}
        onSuccess={onAccountArchived}
      />

      <OpeningCorrectionModal
        key={`opening-correction-${account.id}-${account.version ?? 0}`}
        account={account}
        opened={active?.type === 'opening-correction'}
        onClose={close}
        onRefetchAccount={refreshSelectedAccount}
      />

      <AccountActivitiesDrawer
        account={account}
        opened={active?.type === 'activities'}
        onClose={close}
        onActivityUpdated={refreshSelectedAccount}
      />
    </>
  );
}
