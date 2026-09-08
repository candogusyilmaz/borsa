import { Group, TextInput } from '@mantine/core';
import { useNavigate, useSearch } from '@tanstack/react-router';

export function TradeHistoryFilter() {
  const { q } = useSearch({
    from: '/_authenticated/_member/trades'
  });
  const navigate = useNavigate();

  return (
    <Group justify="space-between">
      <TextInput
        value={q}
        onChange={(event) => navigate({ to: '.', search: (old) => ({ ...old, q: event.target.value }) })}
        placeholder="Search trades"
      />
    </Group>
  );
}
