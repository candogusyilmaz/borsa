import { useLingui } from '@lingui/react/macro';
import { DateTimePicker, type DateTimePickerProps } from '@mantine/dates';
import { parseDateTimeInput } from '~/shared/format/format';
import { useFormat } from '~/shared/format/use-format';

export function LocalizedDateTimePicker(props: DateTimePickerProps) {
  const { t } = useLingui();
  const format = useFormat();

  return (
    <DateTimePicker
      valueFormat={(value) => {
        const parsed = parseDateTimeInput(value);
        return parsed ? format.toShortDateTime(parsed) : value;
      }}
      ariaLabels={{
        nextMonth: t`Next month`,
        previousMonth: t`Previous month`,
        nextYear: t`Next year`,
        previousYear: t`Previous year`,
        nextDecade: t`Next decade`,
        previousDecade: t`Previous decade`,
        monthLevelControl: t`Pick a month`,
        yearLevelControl: t`Pick a year`
      }}
      timePickerProps={{
        hoursInputLabel: t`Hours`,
        minutesInputLabel: t`Minutes`,
        secondsInputLabel: t`Seconds`
      }}
      submitButtonProps={{ 'aria-label': t`Save time` }}
      {...props}
    />
  );
}
