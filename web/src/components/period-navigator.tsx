import { ActionIcon, Group, Text } from '@mantine/core';
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react';

export function PeriodNavigator({
  label,
  onPrev,
  onNext,
  canPrev = true,
  canNext = true
}: {
  label: string;
  onPrev: () => void;
  onNext: () => void;
  canPrev?: boolean;
  canNext?: boolean;
}) {
  return (
    <Group gap={2} align="center" wrap="nowrap">
      <ActionIcon size="xs" variant="subtle" onClick={onPrev} disabled={!canPrev}>
        <IconChevronLeft size={13} />
      </ActionIcon>
      <Text size="xs" fw={600} style={{ minWidth: 80, textAlign: 'center', whiteSpace: 'nowrap' }}>
        {label}
      </Text>
      <ActionIcon size="xs" variant="subtle" onClick={onNext} disabled={!canNext}>
        <IconChevronRight size={13} />
      </ActionIcon>
    </Group>
  );
}
