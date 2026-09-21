import { Button, Text, TextInput } from '@mantine/core';
import { CalendarBlankIcon, ClockCounterClockwiseIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { toDateTimeLocal } from '@/shared/format/date-time';
import { registerOverlay } from '@/shared/overlay';
import classes from './as-of-date.module.css';

export interface AsOfDateResult {
  asOf: string | null;
}

export interface AsOfDateProps {
  accountId: string;
  selectedAsOf: string | null;
}

export function AsOfDate({ accountId, selectedAsOf }: AsOfDateProps) {
  const current = AsOfDateOverlay.useCurrent();
  const [customInput, setCustomInput] = useState(() =>
    selectedAsOf ? toDateTimeLocal(new Date(selectedAsOf)) : toDateTimeLocal(new Date())
  );

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });
  const coverageFrom = accountQuery.data?.coverageFrom;

  function handleLive() {
    current.complete({ asOf: null });
  }

  function handleOpening() {
    if (coverageFrom) {
      current.complete({ asOf: coverageFrom });
    }
  }

  function handleTodayStart() {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    current.complete({ asOf: d.toISOString() });
  }

  function handleYesterday() {
    const d = new Date();
    d.setDate(d.getDate() - 1);
    d.setHours(23, 59, 59, 999);
    current.complete({ asOf: d.toISOString() });
  }

  function handleStartOfMonth() {
    const d = new Date();
    d.setDate(1);
    d.setHours(0, 0, 0, 0);
    current.complete({ asOf: d.toISOString() });
  }

  function handleApplyCustom() {
    if (!customInput) {
      current.complete({ asOf: null });
      return;
    }
    const parsed = new Date(customInput);
    if (!Number.isNaN(parsed.getTime())) {
      current.complete({ asOf: parsed.toISOString() });
    }
  }

  return (
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

        {coverageFrom && (
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
  );
}

export const AsOfDateOverlay = registerOverlay.withResult<AsOfDateResult>()(AsOfDate, {
  name: 'as-of-date',
  title: 'View Balance As Of',
  presentation: 'drawer',
  desktopSize: '400px'
});
