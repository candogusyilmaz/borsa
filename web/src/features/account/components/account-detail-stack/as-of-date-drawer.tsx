import { Button, Text, TextInput } from '@mantine/core';
import { CalendarBlankIcon, ClockCounterClockwiseIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import type { FinancialAccount } from '../../types';
import { toDatetimeLocal } from '../../utils/account-formatters';
import classes from './as-of-date-drawer.module.css';

interface AsOfDateDrawerProps {
  account: FinancialAccount;
  opened: boolean;
  onClose: () => void;
  selectedAsOf: string | null;
  onSelectAsOf: (asOf: string | null) => void;
}

export function AsOfDateDrawer({ account, opened, onClose, selectedAsOf, onSelectAsOf }: AsOfDateDrawerProps) {
  const [customInput, setCustomInput] = useState(() =>
    selectedAsOf ? toDatetimeLocal(new Date(selectedAsOf)) : toDatetimeLocal(new Date())
  );

  function handleLive() {
    onSelectAsOf(null);
    onClose();
  }

  function handleOpening() {
    if (account.coverageFrom) {
      onSelectAsOf(account.coverageFrom);
      onClose();
    }
  }

  function handleTodayStart() {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    onSelectAsOf(d.toISOString());
    onClose();
  }

  function handleYesterday() {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    d.setHours(23, 59, 59, 999);
    onSelectAsOf(d.toISOString());
    onClose();
  }

  function handleStartOfMonth() {
    const d = new Date();
    d.setDate(1);
    d.setHours(0, 0, 0, 0);
    onSelectAsOf(d.toISOString());
    onClose();
  }

  function handleApplyCustom() {
    if (!customInput) {
      onSelectAsOf(null);
      onClose();
      return;
    }
    const parsed = new Date(customInput);
    if (!Number.isNaN(parsed.getTime())) {
      onSelectAsOf(parsed.toISOString());
      onClose();
    }
  }

  return (
    <ResponsiveDrawer opened={opened} onClose={onClose} title="View Balance As Of" desktopSize="400px">
      <div className={classes.drawerContent}>
        <Text size="sm" c="dimmed">
          View a point-in-time calculation of your account balance. Movements recorded after this time will be excluded.
        </Text>

        <div className={classes.presetGrid}>
          <Button
            variant={selectedAsOf === null ? 'filled' : 'default'}
            color="brand"
            className={`${classes.presetBtn} ${classes.liveBalanceBtn}`}
            leftSection={<ClockCounterClockwiseIcon size={18} weight="bold" />}
            onClick={handleLive}>
            Live Balance (Reset)
          </Button>

          {account.coverageFrom && (
            <Button variant="default" className={classes.presetBtn} leftSection={<CalendarBlankIcon size={18} />} onClick={handleOpening}>
              Opening Date
            </Button>
          )}

          <Button variant="default" className={classes.presetBtn} onClick={handleTodayStart}>
            Today (00:00)
          </Button>

          <Button variant="default" className={classes.presetBtn} onClick={handleYesterday}>
            Yesterday
          </Button>

          <Button variant="default" className={classes.presetBtn} onClick={handleStartOfMonth}>
            Start of Month
          </Button>
        </div>

        <div className={classes.customDateSection}>
          <Text size="xs" fw={600} c="dimmed">
            Or choose specific date &amp; time:
          </Text>
          <TextInput
            type="datetime-local"
            size="md"
            value={customInput}
            onChange={(e) => setCustomInput(e.currentTarget.value)}
            aria-label="Custom date and time for balance calculation"
          />
        </div>

        <div className={classes.actionRow}>
          <Button color="brand" size="md" fullWidth onClick={handleApplyCustom}>
            Apply As-Of Date
          </Button>
          {selectedAsOf !== null && (
            <Button variant="subtle" color="gray" size="md" fullWidth onClick={handleLive}>
              Reset to Live
            </Button>
          )}
        </div>
      </div>
    </ResponsiveDrawer>
  );
}
