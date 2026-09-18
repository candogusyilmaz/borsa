import { ActionIcon, Badge, Button, Group, Select, Text, TextInput } from '@mantine/core';
import { PlusIcon, XIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { ALIAS_TYPES, getAliasTypeLabel, MAX_ALIAS_VALUE_LENGTH, MAX_ALIASES_PER_INSTRUMENT } from '../instrument-domain';
import type { AliasType, InstrumentAliasInput } from '../types';
import classes from './instrument.module.css';

interface AliasEditorProps {
  value: InstrumentAliasInput[];
  onChange: (newAliases: InstrumentAliasInput[]) => void;
  disabled?: boolean;
}

export function AliasEditor({ value, onChange, disabled = false }: AliasEditorProps) {
  const [aliasType, setAliasType] = useState<AliasType>('TICKER');
  const [aliasValue, setAliasValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  function handleAdd() {
    const trimmed = aliasValue.trim();
    if (!trimmed) {
      setError('Alias value cannot be empty.');
      return;
    }

    if (trimmed.length > MAX_ALIAS_VALUE_LENGTH) {
      setError(`Alias cannot exceed ${MAX_ALIAS_VALUE_LENGTH} characters.`);
      return;
    }

    if (value.length >= MAX_ALIASES_PER_INSTRUMENT) {
      setError(`Cannot add more than ${MAX_ALIASES_PER_INSTRUMENT} aliases.`);
      return;
    }

    const normalized = trimmed.toUpperCase();
    const isDuplicate = value.some((a) => a.type === aliasType && a.value.trim().toUpperCase() === normalized);

    if (isDuplicate) {
      setError(`Duplicate alias: ${aliasType} with this value already exists.`);
      return;
    }

    setError(null);
    onChange([...value, { type: aliasType, value: trimmed }]);
    setAliasValue('');
  }

  function handleRemove(index: number) {
    const next = [...value];
    next.splice(index, 1);
    onChange(next);
  }

  return (
    <div className={classes.aliasEditor}>
      <Group justify="space-between">
        <div>
          <Text fw={600} size="sm">
            Aliases &amp; Alternate Codes
          </Text>
          <Text size="xs" c="dimmed">
            Map ISINs, tickers, provider codes, or custom user labels (up to {MAX_ALIASES_PER_INSTRUMENT}).
          </Text>
        </div>
        <Badge size="xs" variant="light" color="gray">
          {value.length} / {MAX_ALIASES_PER_INSTRUMENT}
        </Badge>
      </Group>

      {!disabled && (
        <div className={classes.aliasInputRow}>
          <Select
            label="Identifier Type"
            data={ALIAS_TYPES}
            value={aliasType}
            onChange={(v) => v && setAliasType(v as AliasType)}
            size="sm"
          />

          <TextInput
            label="Code / Value"
            placeholder="e.g. US0378331005, AAPL"
            value={aliasValue}
            onChange={(e) => {
              setAliasValue(e.currentTarget.value);
              setError(null);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleAdd();
              }
            }}
            error={error}
            size="sm"
          />

          <Button
            variant="default"
            size="sm"
            leftSection={<PlusIcon size={16} weight="bold" />}
            onClick={handleAdd}
            disabled={!aliasValue.trim() || value.length >= MAX_ALIASES_PER_INSTRUMENT}
            style={{ minHeight: 36 }}>
            Add
          </Button>
        </div>
      )}

      {value.length > 0 ? (
        <div className={classes.aliasTagList}>
          {value.map((alias, idx) => (
            <Badge
              key={`${alias.type}:${alias.value}`}
              variant="light"
              color="indigo"
              size="md"
              rightSection={
                !disabled ? (
                  <ActionIcon
                    size="xs"
                    color="indigo"
                    radius="xl"
                    variant="transparent"
                    onClick={() => handleRemove(idx)}
                    aria-label={`Remove alias ${alias.value}`}>
                    <XIcon size={12} weight="bold" />
                  </ActionIcon>
                ) : undefined
              }>
              <strong>{getAliasTypeLabel(alias.type)}:</strong> {alias.value}
            </Badge>
          ))}
        </div>
      ) : (
        <Text size="xs" c="dimmed">
          No aliases assigned.
        </Text>
      )}
    </div>
  );
}
