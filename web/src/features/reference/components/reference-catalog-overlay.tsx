import { Badge, Button, Group, Loader, Tabs, Text, TextInput } from '@mantine/core';
import {
  BookOpenIcon,
  CalendarBlankIcon,
  CaretDownIcon,
  CaretUpIcon,
  CoinsIcon,
  GlobeHemisphereWestIcon,
  MagnifyingGlassIcon,
  StorefrontIcon
} from '@phosphor-icons/react';
import { useMemo, useState } from 'react';
import { $api } from '@/api/client';
import { registerOverlay } from '@/shared/overlay';
import { CountrySelect } from './country-select';
import classes from './reference-catalog.module.css';

export function ReferenceCatalog() {
  const current = ReferenceCatalogOverlay.useCurrent();
  const [tab, setTab] = useState<string | null>('markets');
  const [search, setSearch] = useState('');
  const [selectedCountry, setSelectedCountry] = useState<string | null>(null);
  const [expandedMarketId, setExpandedMarketId] = useState<string | null>(null);

  const { data: markets, isLoading: marketsLoading } = $api.useQuery('get', '/api/v1/reference/markets');
  const { data: currencies, isLoading: currenciesLoading } = $api.useQuery('get', '/api/v1/reference/currencies');
  const { data: countries, isLoading: countriesLoading } = $api.useQuery('get', '/api/v1/reference/countries');

  const query = search.trim().toLowerCase();

  const filteredMarkets = useMemo(() => {
    if (!markets) return [];
    return markets.filter((m) => {
      if (selectedCountry && m.countryCode !== selectedCountry) {
        return false;
      }
      if (!query) return true;
      return (
        m.code.toLowerCase().includes(query) ||
        m.name.toLowerCase().includes(query) ||
        (m.countryCode?.toLowerCase().includes(query) ?? false) ||
        m.timeZone.toLowerCase().includes(query)
      );
    });
  }, [markets, query, selectedCountry]);

  const filteredCurrencies = useMemo(() => {
    if (!currencies) return [];
    if (!query) return currencies;
    return currencies.filter(
      (c) => c.code.toLowerCase().includes(query) || c.name.toLowerCase().includes(query) || c.symbol.toLowerCase().includes(query)
    );
  }, [currencies, query]);

  const filteredCountries = useMemo(() => {
    if (!countries) return [];
    if (!query) return countries;
    return countries.filter((c) => c.code.toLowerCase().includes(query) || c.name.toLowerCase().includes(query));
  }, [countries, query]);

  return (
    <div className={classes.container}>
      <TextInput
        placeholder="Filter reference data..."
        leftSection={<MagnifyingGlassIcon size={16} />}
        value={search}
        onChange={(e) => setSearch(e.currentTarget.value)}
        className={classes.searchBox}
        autoFocus
      />

      <Tabs value={tab} onChange={setTab}>
        <Tabs.List grow>
          <Tabs.Tab value="markets" leftSection={<StorefrontIcon size={16} />}>
            Markets {markets ? `(${filteredMarkets.length})` : ''}
          </Tabs.Tab>
          <Tabs.Tab value="currencies" leftSection={<CoinsIcon size={16} />}>
            Currencies {currencies ? `(${filteredCurrencies.length})` : ''}
          </Tabs.Tab>
          <Tabs.Tab value="countries" leftSection={<GlobeHemisphereWestIcon size={16} />}>
            Countries {countries ? `(${filteredCountries.length})` : ''}
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="markets" pt="sm">
          <div style={{ marginBottom: '0.5rem' }}>
            <CountrySelect placeholder="Filter by Country" value={selectedCountry} onChange={setSelectedCountry} clearable size="xs" />
          </div>

          {marketsLoading ? (
            <Group justify="center" p="xl">
              <Loader size="sm" />
            </Group>
          ) : filteredMarkets.length === 0 ? (
            <div className={classes.emptyState}>
              <StorefrontIcon size={32} />
              <Text size="sm">No markets match "{search}"</Text>
            </div>
          ) : (
            <div className={classes.listGrid}>
              {filteredMarkets.map((m) => (
                <div key={m.id} className={classes.cardItem}>
                  <div className={classes.cardHeader}>
                    <div className={classes.cardTitle}>{m.name}</div>
                    <Badge color="brand" variant="light" size="sm" className={classes.cardCode}>
                      {m.code}
                    </Badge>
                  </div>
                  <div className={classes.cardMeta}>
                    {m.countryCode && (
                      <span className={classes.metaItem}>
                        <strong>Country:</strong> {m.countryCode}
                      </span>
                    )}
                    <span className={classes.metaItem}>
                      <strong>Time Zone:</strong> {m.timeZone}
                    </span>
                    <span className={classes.metaItem}>
                      <strong>Type:</strong> {m.marketType}
                    </span>
                    {m.primaryQuotationCurrency && (
                      <span className={classes.metaItem}>
                        <strong>Primary:</strong> {m.primaryQuotationCurrency}
                      </span>
                    )}
                  </div>
                  {m.quotationCurrencies && m.quotationCurrencies.length > 0 && (
                    <div className={classes.currenciesBadgeGroup}>
                      {m.quotationCurrencies.map((c) => (
                        <Badge key={c} size="xs" variant="outline" color="gray">
                          {c}
                        </Badge>
                      ))}
                    </div>
                  )}

                  <Button
                    size="xs"
                    variant="subtle"
                    color="brand"
                    leftSection={<CalendarBlankIcon size={14} />}
                    rightSection={
                      expandedMarketId === m.id ? <CaretUpIcon size={12} weight="bold" /> : <CaretDownIcon size={12} weight="bold" />
                    }
                    onClick={() => setExpandedMarketId(expandedMarketId === m.id ? null : m.id)}
                    style={{ minHeight: 32, alignSelf: 'flex-start', marginTop: 4 }}>
                    {expandedMarketId === m.id ? 'Hide Schedule' : 'Trading Schedule'}
                  </Button>

                  {expandedMarketId === m.id && <MarketCalendarSection marketId={m.id} timeZone={m.timeZone} />}
                </div>
              ))}
            </div>
          )}
        </Tabs.Panel>

        <Tabs.Panel value="currencies" pt="sm">
          {currenciesLoading ? (
            <Group justify="center" p="xl">
              <Loader size="sm" />
            </Group>
          ) : filteredCurrencies.length === 0 ? (
            <div className={classes.emptyState}>
              <CoinsIcon size={32} />
              <Text size="sm">No currencies match "{search}"</Text>
            </div>
          ) : (
            <div className={classes.listGrid}>
              {filteredCurrencies.map((c) => (
                <div key={c.code} className={classes.cardItem}>
                  <div className={classes.cardHeader}>
                    <Text fw={600} size="sm">
                      {c.name}
                    </Text>
                    <Badge color="teal" variant="light" size="sm" className={classes.cardCode}>
                      {c.code} ({c.symbol})
                    </Badge>
                  </div>
                  <div className={classes.cardMeta}>
                    <span className={classes.metaItem}>
                      <strong>Minor Unit:</strong> {c.minorUnit ?? 2} decimals
                    </span>
                    <span className={classes.metaItem}>
                      <strong>Status:</strong> {c.active !== false ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Tabs.Panel>

        <Tabs.Panel value="countries" pt="sm">
          {countriesLoading ? (
            <Group justify="center" p="xl">
              <Loader size="sm" />
            </Group>
          ) : filteredCountries.length === 0 ? (
            <div className={classes.emptyState}>
              <GlobeHemisphereWestIcon size={32} />
              <Text size="sm">No countries match "{search}"</Text>
            </div>
          ) : (
            <div className={classes.listGrid}>
              {filteredCountries.map((country) => (
                <div key={country.code} className={classes.cardItem}>
                  <div className={classes.cardHeader}>
                    <Text fw={600} size="sm">
                      {country.name}
                    </Text>
                    <Badge color="blue" variant="light" size="sm" className={classes.cardCode}>
                      {country.code}
                    </Badge>
                  </div>
                  <div className={classes.cardMeta}>
                    <span className={classes.metaItem}>
                      <strong>Status:</strong> {country.active !== false ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Tabs.Panel>
      </Tabs>

      <Button variant="default" size="md" fullWidth onClick={() => current.dismiss('cancelled')} mt="xs">
        Close Catalogue
      </Button>
    </div>
  );
}

function MarketCalendarSection({ marketId, timeZone }: { marketId: string; timeZone: string }) {
  const today = new Date().toISOString().slice(0, 10);
  const nextWeek = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);

  const {
    data: calendar,
    isLoading,
    isError
  } = $api.useQuery('get', '/api/v1/reference/markets/{marketId}/calendar', {
    params: {
      path: { marketId },
      query: { from: today, to: nextWeek }
    }
  });

  if (isLoading) {
    return (
      <div className={classes.calendarSection}>
        <Group justify="center" p="xs">
          <Loader size="xs" />
        </Group>
      </div>
    );
  }

  if (isError || !calendar) {
    return (
      <div className={classes.calendarSection}>
        <Text size="xs" c="dimmed">
          Could not load market schedule for this period.
        </Text>
      </div>
    );
  }

  return (
    <div className={classes.calendarSection}>
      <Group justify="space-between" mb={4}>
        <Text size="xs" fw={600}>
          7-Day Trading Schedule ({timeZone})
        </Text>
        <Badge
          size="xs"
          variant="light"
          color={calendar.coverageStatus === 'COMPLETE' ? 'teal' : calendar.coverageStatus === 'PARTIAL' ? 'orange' : 'gray'}>
          {calendar.coverageStatus}
        </Badge>
      </Group>

      {calendar.sessions.slice(0, 5).map((session) => (
        <div key={session.date} className={classes.calendarSessionRow}>
          <span>{session.date}</span>
          <Group gap={4}>
            <Badge size="xs" variant="light" color={session.sessionStatus === 'OPEN' ? 'teal' : 'gray'}>
              {session.sessionStatus}
            </Badge>
            {session.opensAt && session.closesAt && (
              <span style={{ fontSize: '0.6875rem', color: 'var(--mantine-color-dimmed)' }}>
                {session.opensAt.slice(0, 5)} &ndash; {session.closesAt.slice(0, 5)}
              </span>
            )}
          </Group>
        </div>
      ))}
    </div>
  );
}

export const ReferenceCatalogOverlay = registerOverlay(ReferenceCatalog, {
  name: 'reference-catalog',
  title: (
    <Group gap="xs">
      <BookOpenIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Reference Catalogue
      </Text>
    </Group>
  ),
  presentation: 'drawer',
  size: 'lg'
});
