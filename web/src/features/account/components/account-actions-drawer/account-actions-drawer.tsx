import { ArchiveIcon, ArrowClockwiseIcon, BankIcon, CaretRightIcon, GearIcon, InfoIcon } from '@phosphor-icons/react';
import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import type { FinancialAccount } from '../../types';
import { getAccountKindLabel, getTrackingModeLabel } from '../../utils/account-formatters';
import { useAccountDetailOverlay } from '../account-detail-overlay/account-detail-overlay-provider';
import classes from './account-actions-drawer.module.css';

interface AccountActionsDrawerProps {
  account: FinancialAccount;
  opened: boolean;
  onClose: () => void;
  onRefresh: () => void;
}

export function AccountActionsDrawer({ account, opened, onClose, onRefresh }: AccountActionsDrawerProps) {
  const { open } = useAccountDetailOverlay();
  return (
    <ResponsiveDrawer opened={opened} onClose={onClose} title="Account Actions">
      <div className={classes.drawerContent}>
        {/* Account Banner */}
        <div className={classes.accountBanner}>
          <div className={classes.iconSquircle} aria-hidden="true">
            <BankIcon size={24} weight="duotone" />
          </div>
          <div className={classes.bannerMeta}>
            <span className={classes.bannerName}>{account.name}</span>
            <span className={classes.bannerSubtitle}>
              {getAccountKindLabel(account.kind)} &bull; {getTrackingModeLabel(account.trackingMode)} &bull; {account.currency}
            </span>
          </div>
        </div>

        {/* 1. Refresh */}
        <button
          type="button"
          className={classes.actionItem}
          onClick={() => {
            onRefresh();
            onClose();
          }}>
          <div className={classes.actionItemLeft}>
            <ArrowClockwiseIcon size={20} />
            <span>Refresh Account Data</span>
          </div>
          <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
        </button>

        {/* 2. Settings & Policies */}
        <button type="button" className={classes.actionItem} disabled={account.archived} onClick={() => open({ type: 'settings' })}>
          <div className={classes.actionItemLeft}>
            <GearIcon size={20} />
            <span>Settings &amp; Policies</span>
          </div>
          <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
        </button>

        {/* 3. Account Information */}
        <button type="button" className={classes.actionItem} onClick={() => open({ type: 'info' })}>
          <div className={classes.actionItemLeft}>
            <InfoIcon size={20} />
            <span>Account Information</span>
          </div>
          <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
        </button>

        <div className={classes.divider} />

        {/* 4. Archive Account */}
        <button
          type="button"
          className={`${classes.actionItem} ${classes.destructiveItem}`}
          disabled={account.archived}
          onClick={() => open({ type: 'archive' })}>
          <div className={classes.actionItemLeft}>
            <ArchiveIcon size={20} />
            <span>{account.archived ? 'Account is Archived' : 'Archive Account'}</span>
          </div>
          <CaretRightIcon size={16} />
        </button>
      </div>
    </ResponsiveDrawer>
  );
}
