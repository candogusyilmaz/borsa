import { Alert, Button, Skeleton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { WarningCircleIcon } from '@phosphor-icons/react';
import { Outlet, useNavigate, useParams } from '@tanstack/react-router';
import { createContext, useContext } from 'react';
import { $api } from '@/api/client';
import type { AccountsLayoutContext } from '../../types';
import { AccountCarousel } from '../account-carousel';
import { AccountLayoutHeader } from '../account-layout-header';
import { AccountPickerDrawer } from '../account-picker-drawer';
import { CreateAccountModal } from '../create-account-modal/create-account-modal';
import classes from './accounts-layout.module.css';

const AccountsContext = createContext<AccountsLayoutContext | null>(null);

export function useAccountsLayout(): AccountsLayoutContext {
  const context = useContext(AccountsContext);
  if (!context) {
    throw new Error('useAccountsLayout must be used within an AccountsLayout');
  }
  return context;
}

export function AccountsLayout() {
  const navigate = useNavigate();
  const params = useParams({ strict: false }) as { accountId?: string };
  const currentAccountId = params.accountId;

  // Authoritative Query: fetch all accounts once, include archived for switching
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: {
        includeArchived: true
      }
    }
  });

  const rawAccounts = accountsQuery.data ?? [];
  const activeAccounts = rawAccounts.filter((a) => !a.archived);

  // Selected account for carousel:
  // If currentAccountId matches an account in rawAccounts, use it.
  // Otherwise default to first active account, or first raw account.
  const selectedAccount =
    (currentAccountId ? rawAccounts.find((a) => a.id === currentAccountId) : null) ??
    (activeAccounts.length > 0 ? activeAccounts[0] : rawAccounts.length > 0 ? rawAccounts[0] : null);

  const selectedAccountId = currentAccountId ?? selectedAccount?.id ?? '';

  // Derive carousel slides: include selected account if it is archived, otherwise all active accounts.
  // If all accounts are archived (zero active), provide all raw accounts so carousel navigation functions.
  const carouselAccounts =
    activeAccounts.length > 0
      ? selectedAccount?.archived && !activeAccounts.some((a) => a.id === selectedAccount.id)
        ? [selectedAccount, ...activeAccounts]
        : activeAccounts
      : rawAccounts;

  // Modals / Drawers at layout level
  const [createModalOpened, { open: openCreateModal, close: closeCreateModal }] = useDisclosure(false);
  const [pickerDrawerOpened, { open: openPickerDrawer, close: closePickerDrawer }] = useDisclosure(false);

  function handleSelectAccount(accountId: string) {
    if (accountId === currentAccountId) return;
    navigate({
      to: '/app/accounts/$accountId',
      params: { accountId },
      replace: true
    });
  }

  const contextValue: AccountsLayoutContext = {
    accounts: rawAccounts,
    activeAccounts,
    isLoading: accountsQuery.isLoading,
    isFetching: accountsQuery.isFetching,
    isError: accountsQuery.isError,
    refetchAccounts: () => accountsQuery.refetch(),
    openCreateModal,
    openPickerDrawer
  };

  return (
    <AccountsContext.Provider value={contextValue}>
      <section className={classes.container} aria-labelledby="accounts-page-title">
        {/* 1. Header Row */}
        <AccountLayoutHeader activeCount={activeAccounts.length} onOpenCreate={openCreateModal} />

        {/* 2. Loading / Error / Carousel States */}
        {accountsQuery.isLoading && (
          <div className={classes.heroRegion}>
            <Skeleton height={200} radius="lg" />
          </div>
        )}

        {accountsQuery.isError && (
          <Alert icon={<WarningCircleIcon size={20} />} title="Could not load accounts" color="red" variant="light">
            We encountered an issue fetching your accounts. Please check your connection.
            <Button size="xs" variant="outline" color="red" mt="xs" style={{ minHeight: 44 }} onClick={() => accountsQuery.refetch()}>
              Retry
            </Button>
          </Alert>
        )}

        {!accountsQuery.isLoading && !accountsQuery.isError && rawAccounts.length > 0 && (
          <div className={classes.heroRegion}>
            <AccountCarousel accounts={carouselAccounts} selectedAccountId={selectedAccountId} onSelectAccount={handleSelectAccount} />
          </div>
        )}

        {/* 3. Child Outlet */}
        <Outlet />

        {/* 4. Global Modals / Drawers */}
        <CreateAccountModal
          opened={createModalOpened}
          onClose={closeCreateModal}
          onSuccess={(created) => {
            navigate({
              to: '/app/accounts/$accountId',
              params: { accountId: created.id },
              replace: !currentAccountId
            });
          }}
        />

        <AccountPickerDrawer
          accounts={rawAccounts}
          selectedAccountId={selectedAccountId}
          opened={pickerDrawerOpened}
          onClose={closePickerDrawer}
          onSelectAccount={(accountId) => {
            navigate({
              to: '/app/accounts/$accountId',
              params: { accountId },
              replace: true
            });
            closePickerDrawer();
          }}
        />
      </section>
    </AccountsContext.Provider>
  );
}
