import { ActionIcon, Group, Text } from '@mantine/core';
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react';

export function PeriodNavigator({
  label,
  onPrev,
  onNext,
  canPrev,
  canNext
}: {
  label: string;
  onPrev: () => void;
  onNext: () => void;
  canPrev: boolean;
  canNext: boolean;
}) {
  return (
    <Group gap={4} align="center" wrap="nowrap">
      <ActionIcon variant="subtle" aria-label="Previous period" onClick={onPrev} disabled={!canPrev}>
        <IconChevronLeft size={16} />
      </ActionIcon>
      <Text size="sm" fw={550}>
        {label}
      </Text>
      <ActionIcon variant="subtle" aria-label="Next period" onClick={onNext} disabled={!canNext}>
        <IconChevronRight size={16} />
      </ActionIcon>
    </Group>
  );
}
