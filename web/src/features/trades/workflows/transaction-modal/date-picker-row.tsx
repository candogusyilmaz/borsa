import { Trans, useLingui } from '@lingui/react/macro';
import { IconChevronDown } from '@tabler/icons-react';
import clsx from 'clsx';
import dayjs from 'dayjs';
import { LocalizedDateTimePicker } from '~/shared/components/localized-date-time-picker';
import { useFormat } from '~/shared/format/use-format';
import styles from './transaction-modal.module.css';

export function DatePickerRow({ value, onChange }: { value: Date; onChange: (date: Date) => void }) {
  const { t } = useLingui();
  const format = useFormat();

  function formatDateLabel(raw: string) {
    const parsed = dayjs(raw);
    if (!parsed.isValid()) return raw;

    const date = parsed.toDate();
    const time = format.date(date, { hour: '2-digit', minute: '2-digit' });
    const now = dayjs();

    if (parsed.isSame(now, 'day')) return t`Today, ${time}`;
    if (parsed.isSame(now.subtract(1, 'day'), 'day')) return t`Yesterday, ${time}`;

    const dayPart =
      parsed.year() === now.year()
        ? format.date(date, { day: 'numeric', month: 'short' })
        : format.date(date, { day: 'numeric', month: 'short', year: 'numeric' });
    return `${dayPart}, ${time}`;
  }

  return (
    <div className={styles.metaRow}>
      <span className={styles.metaLabel}>
        <Trans>Date</Trans>
      </span>
      <LocalizedDateTimePicker
        variant="unstyled"
        size="xs"
        highlightToday
        aria-label={t`Date`}
        value={dayjs(value).format('YYYY-MM-DD HH:mm:ss')}
        valueFormat={formatDateLabel}
        onChange={(next) => {
          if (!next) return;
          const parsed = dayjs(next);
          if (parsed.isValid()) onChange(parsed.toDate());
        }}
        rightSection={<IconChevronDown size={13} stroke={1.9} className={styles.chevron} aria-hidden="true" />}
        rightSectionWidth={18}
        rightSectionPointerEvents="none"
        popoverProps={{
          withinPortal: true,
          zIndex: 350,
          position: 'top-end',
          offset: 6,
          middlewares: { flip: true, shift: { padding: 12 } },
          classNames: { dropdown: clsx(styles.scope, styles.dropdown, styles.dateDropdown) }
        }}
        classNames={{
          root: styles.dateTriggerRoot,
          input: styles.dateTriggerInput,
          day: styles.calDay,
          monthsListControl: styles.calControl,
          yearsListControl: styles.calControl,
          timeWrapper: styles.calTimeWrapper,
          timeInput: styles.calTimeInput,
          submitButton: styles.calSubmitBtn
        }}
      />
    </div>
  );
}
