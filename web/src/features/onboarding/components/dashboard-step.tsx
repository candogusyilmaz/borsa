import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Badge, Button, Flex, Group, Radio, SimpleGrid, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { IconCheck, IconLayoutDashboard } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import type React from 'react';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { useLocale } from '~/shared/i18n/locale-context';
import { sleep } from '~/shared/utils/sleep';
import classes from '../pages/onboarding.module.css';
import type { OnboardingRequest } from '../types';

interface DashboardStepProps {
  onBack: () => void;
  onboardingRequest: OnboardingRequest;
  setOnboardingRequest: React.Dispatch<React.SetStateAction<OnboardingRequest>>;
}

export function DashboardStep({ onboardingRequest, onBack }: DashboardStepProps) {
  const { t } = useLingui();
  const { formatLocale, language } = useLocale();
  const { data: currencies } = $api.useQuery(
    'get',
    '/api/currencies',
    {},
    {
      select: (v) => v.map((c) => getCurrencyInfo(c.label.slice(0, 3), language, formatLocale))
    }
  );
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const small = useMediaQuery('(max-width: 600px)');
  const smaller = useMediaQuery('(max-width: 400px)');

  const [name, setName] = useState<string>(onboardingRequest.dashboard.name);
  const [currency, setCurrency] = useState<string>('USD');

  const mutation = $api.useMutation('post', '/api/onboarding/complete', {
    onSuccess: async () => {
      await queryClient.invalidateQueries();
      await sleep(1);
      await navigate({ to: '/dashboard' });
    }
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    mutation.mutate({
      body: {
        portfolio: {
          color: onboardingRequest.portfolio.color,
          portfolioName: onboardingRequest.portfolio.name
        },
        currencyCode: currency,
        dashboardName: name.trim()
      }
    });
  };

  return (
    <Stack>
      <Stack gap={2} ta="center">
        <Title order={2}>
          <Trans>Dashboard View</Trans>
        </Title>
        <Text c="dimmed" fz="sm">
          <Trans>Create an aggregate view for your portfolios.</Trans>
        </Text>
      </Stack>

      <form onSubmit={handleSubmit} className="space-y-6">
        <Stack gap={35}>
          <TextInput
            name="name"
            label={t`DASHBOARD NAME`}
            placeholder={t`e.g. My Investments`}
            leftSection={<IconLayoutDashboard size={18} />}
            leftSectionWidth={50}
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
            styles={{
              label: {
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--mantine-color-dimmed)',
                marginBottom: 4,
                marginLeft: 2
              },
              input: {
                height: 50,
                fontSize: 16,
                fontWeight: 300
              }
            }}
          />

          <Stack gap={4}>
            <Text fz="12" fw={600} c="var(--mantine-color-dimmed)" tt="uppercase">
              <Trans>Reporting Currency</Trans>
            </Text>
            <Radio.Group value={currency} onChange={setCurrency} classNames={{ root: classes.currencyRadioRoot }}>
              <SimpleGrid spacing="xs" cols={{ base: smaller ? 1 : 2, xs: small ? 2 : 3 }}>
                {currencies?.map((currency) => (
                  <Radio.Card key={currency.currency} radius={14} value={currency.currency!}>
                    <Group gap={8} p={10}>
                      <Badge
                        radius={10}
                        h={35}
                        w={35}
                        fz={currency.symbol!.length > 1 ? 10 : 14}
                        p={0}
                        variant="light"
                        color="var(--mantine-primary-color-filled)">
                        {currency.symbol}
                      </Badge>
                      <Stack flex={1} gap={0} style={{ overflow: 'hidden' }}>
                        <Text fz={13} fw={600}>
                          {currency.currency}
                        </Text>
                        <Text
                          fz={10}
                          c="var(--mantine-color-dimmed)"
                          style={{ textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                          {currency.name}
                        </Text>
                      </Stack>
                    </Group>
                  </Radio.Card>
                ))}
              </SimpleGrid>
            </Radio.Group>
          </Stack>

          <Stack gap={4}>
            <Text fz="12" fw={600} c="var(--mantine-color-dimmed)" tt="uppercase">
              <Trans>Linked Portfolios</Trans>
            </Text>
            <Group
              gap={14}
              p={14}
              style={{
                border: `2px solid ${onboardingRequest.portfolio.color}`,
                borderRadius: 12,
                cursor: 'pointer'
              }}>
              <div
                style={{
                  width: 5,
                  height: 24,
                  borderRadius: 20,
                  backgroundColor: onboardingRequest.portfolio.color
                }}
              />
              <Stack gap={1}>
                <Text fz="13" fw={700} c="var(--mantine-color-text)">
                  {onboardingRequest.portfolio.name}
                </Text>
                <Text fz="10" fw={500} lts={0.3} c="var(--mantine-color-dimmed)" tt="uppercase">
                  <Plural value={onboardingRequest.portfolio.trades.length} one="# position" other="# positions" />
                </Text>
              </Stack>
              <ThemeIcon size="sm" ml="auto" variant="filled" radius={'xl'} color={onboardingRequest.portfolio.color}>
                <IconCheck size={16} />
              </ThemeIcon>
            </Group>
          </Stack>

          <Flex direction={small ? 'column-reverse' : 'row'} gap="sm">
            <Button
              flex={small ? undefined : 1}
              type="button"
              tt="uppercase"
              lts={1}
              loading={mutation.isPending}
              h={40}
              variant="subtle"
              c="dimmed"
              onClick={onBack}>
              <Trans comment="Move to the previous onboarding step">Back</Trans>
            </Button>
            <Button
              flex={small ? undefined : 2}
              type="submit"
              tt="uppercase"
              lts={1}
              loading={mutation.isPending}
              h={40}
              color="var(--mantine-primary-color-filled)"
              disabled={name.trim().length === 0}>
              <Trans>Launch Workspace</Trans>
            </Button>
          </Flex>
        </Stack>
      </form>
    </Stack>
  );
}

function getCurrencyInfo(currency: string, language: string, locale: string) {
  const name = new Intl.DisplayNames([language], { type: 'currency' }).of(currency);

  const symbol = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    currencyDisplay: 'symbol'
  })
    .formatToParts(0)
    .find((p) => p.type === 'currency')?.value;

  return { currency, name, symbol };
}
