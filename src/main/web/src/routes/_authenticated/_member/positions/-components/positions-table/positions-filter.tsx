import { Group, TextInput } from '@mantine/core';
import { useNavigate, useSearch } from '@tanstack/react-router';

export function PositionsFilter() {
  const { q } = useSearch({
    from: '/_authenticated/_member/positions'
  });
  const navigate = useNavigate();

  return (
    <Group justify="space-between">
      <TextInput
        value={q}
        onChange={(event) => navigate({ to: '.', search: (old) => ({ ...old, q: event.target.value }) })}
        placeholder="Search positions"
      />
    </Group>
  );
}
