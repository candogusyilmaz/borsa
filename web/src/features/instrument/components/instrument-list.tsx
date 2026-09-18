import { Alert, Button, CloseButton, Group, Select, Skeleton, Stack, Switch, Text, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { MagnifyingGlassIcon, PlusIcon, StackIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { MarketSelect } from '@/features/reference';
import { INSTRUMENT_TYPES, MAX_QUERY_LENGTH } from '../instrument-domain';
import type { InstrumentType } from '../types';
import classes from './instrument.module.css';
import { InstrumentCard } from './instrument-card';

interface InstrumentListProps {
  onSelectInstrument: (instrumentId: string) => void;
  onOpenCreate: () => void;
}

export function InstrumentList({ onSelectInstrument, onOpenCreate }: InstrumentListProps) {
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch] = useDebouncedValue(searchInput, 300);
  const [marketId, setMarketId] = useState<string | null>(null);
  const [instrumentType, setInstrumentType] = useState<InstrumentType | 'ALL'>('ALL');
  const [includeInactive, setIncludeInactive] = useState(false);
  const [page, setPage] = useState(0);

  // CRITICAL: Normalize query string so empty/whitespace is sent as undefined (null),
  // preventing backend 400 validation error in InstrumentSearchService.java
  const trimmedQuery = debouncedSearch.trim();
  const validQuery = trimmedQuery.length > 0 && trimmedQuery.length <= MAX_QUERY_LENGTH ? trimmedQuery : undefined;

  const searchQuery = $api.useQuery('get', '/api/v1/reference/instruments', {
    params: {
      query: {
        query: validQuery,
        marketId: marketId || undefined,
        type: instrumentType !== 'ALL' ? instrumentType : undefined,
        includeInactive,
        pageable: {
          page,
          size: 25,
          sort: ['name,asc']
        }
      }
    }
  });

  const items = searchQuery.data?.items ?? [];
  const hasNext = searchQuery.data?.hasNext ?? false;

  return (
    <div className={classes.container}>
      {/* 1. Header Bar */}
      <div className={classes.headerBar}>
        <div className={classes.titleArea}>
          <StackIcon size={24} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <div>
            <Text fw={700} size="lg">
              Financial Instruments
            </Text>
            <Text size="xs" c="dimmed">
              Global catalog and user-managed securities &amp; assets
            </Text>
          </div>
        </div>

        <Button color="brand" size="sm" leftSection={<PlusIcon size={16} weight="bold" />} onClick={onOpenCreate} style={{ minHeight: 36 }}>
          New Instrument
        </Button>
      </div>

      {/* 2. Filter Bar */}
      <div className={classes.filterBar}>
        <TextInput
          placeholder="Search by symbol, name, or alias (e.g. AAPL, Apple, US0378331005)..."
          leftSection={<MagnifyingGlassIcon size={16} />}
          rightSection={
            searchInput ? (
              <CloseButton
                size="sm"
                onClick={() => {
                  setSearchInput('');
                  setPage(0);
                }}
                aria-label="Clear search input"
              />
            ) : null
          }
          value={searchInput}
          onChange={(e) => {
            setSearchInput(e.currentTarget.value);
            setPage(0);
          }}
          size="md"
        />

        <div className={classes.filterControls}>
          <MarketSelect
            placeholder="All Markets"
            value={marketId}
            onChange={(val) => {
              setMarketId(val);
              setPage(0);
            }}
            clearable
            size="sm"
          />

          <Select
            placeholder="All Asset Types"
            data={[{ value: 'ALL', label: 'All Asset Types' }, ...INSTRUMENT_TYPES]}
            value={instrumentType}
            onChange={(val) => {
              setInstrumentType((val as InstrumentType | 'ALL') || 'ALL');
              setPage(0);
            }}
            size="sm"
          />

          <Switch
            label="Include Inactive"
            checked={includeInactive}
            onChange={(e) => {
              setIncludeInactive(e.currentTarget.checked);
              setPage(0);
            }}
            size="sm"
            style={{ paddingBottom: 6 }}
          />
        </div>
      </div>

      {/* 3. Loading State */}
      {searchQuery.isLoading && (
        <Stack gap="sm">
          <Skeleton height={70} radius="md" />
          <Skeleton height={70} radius="md" />
          <Skeleton height={70} radius="md" />
        </Stack>
      )}

      {/* 4. Error State */}
      {searchQuery.isError && (
        <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
          Could not load instruments matching current criteria.
          <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => searchQuery.refetch()}>
            Retry Search
          </Button>
        </Alert>
      )}

      {/* 5. Empty State */}
      {!searchQuery.isLoading && !searchQuery.isError && items.length === 0 && (
        <div className={classes.emptyState}>
          <StackIcon size={36} weight="duotone" color="var(--mantine-color-dimmed)" />
          <Text size="sm" fw={600}>
            No Instruments Found
          </Text>
          <Text size="xs" c="dimmed" maw={320}>
            {trimmedQuery
              ? `No securities or assets matched "${trimmedQuery}". You can create a manual instrument for custom assets.`
              : 'No instruments found for the selected filters.'}
          </Text>
          <Button size="md" color="brand" mt="xs" onClick={onOpenCreate} leftSection={<PlusIcon size={18} weight="bold" />}>
            Create Manual Instrument
          </Button>
        </div>
      )}

      {/* 6. List Items */}
      {!searchQuery.isLoading && items.length > 0 && (
        <div className={classes.instrumentList}>
          {items.map((instrument) => (
            <InstrumentCard key={instrument.id} instrument={instrument} onSelect={onSelectInstrument} />
          ))}
        </div>
      )}

      {/* 7. Pagination Bar */}
      {(items.length > 0 || page > 0) && (
        <div className={classes.paginationBar}>
          <Text size="xs" c="dimmed">
            Page {page + 1}
          </Text>

          <Group gap="xs">
            <Button
              size="xs"
              variant="default"
              disabled={page === 0 || searchQuery.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <Button size="xs" variant="default" disabled={!hasNext || searchQuery.isFetching} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </Group>
        </div>
      )}
    </div>
  );
}
