import { Badge, Button, Flex, Group, Radio, SimpleGrid, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { IconCheck, IconLayoutDashboard } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import type React from 'react';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { sleep } from '~/lib/sleep';
import classes from '../onboarding.module.css';
import type { OnboardingRequest } from '../route';

interface DashboardStepProps {
  onBack: () => void;
  onboardingRequest: OnboardingRequest;
  setOnboardingRequest: React.Dispatch<React.SetStateAction<OnboardingRequest>>;
}

export function DashboardStep({ onboardingRequest, onBack }: DashboardStepProps) {
  const { data: currencies } = $api.useQuery(
    'get',
    '/api/currencies',
    {},
    {
      select: (v) => v.map((c) => getCurrencyInfo(c.label.slice(0, 3)))
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
        <Title order={2}>Dashboard View</Title>
        <Text c="dimmed" fz="sm">
          Create an aggregate view for your portfolios.
        </Text>
      </Stack>

      <form onSubmit={handleSubmit} className="space-y-6">
        <Stack gap={35}>
          <TextInput
            name="name"
            label="DASHBOARD NAME"
            placeholder="e.g. My Investments"
            leftSection={<IconLayoutDashboard size={18} />}
            leftSectionWidth={50}
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
            styles={{
              label: {
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--text-muted)',
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
            <Text fz="12" fw={600} c="var(--text-muted)" tt="uppercase">
              Reporting Currency
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
                        color="var(--accent-color)">
                        {currency.symbol}
                      </Badge>
                      <Stack flex={1} gap={0} style={{ overflow: 'hidden' }}>
                        <Text fz={13} fw={600}>
                          {currency.currency}
                        </Text>
                        <Text fz={10} c="var(--text-muted)" style={{ textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
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
            <Text fz="12" fw={600} c="var(--text-muted)" tt="uppercase">
              Linked Portfolios
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
                <Text fz="13" fw={700} c="var(--text-main)">
                  {onboardingRequest.portfolio.name}
                </Text>
                <Text fz="10" fw={500} lts={0.3} c="var(--text-muted)" tt="uppercase">
                  {onboardingRequest.portfolio.trades.length} Positions
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
              Back
            </Button>
            <Button
              flex={small ? undefined : 2}
              type="submit"
              tt="uppercase"
              lts={1}
              loading={mutation.isPending}
              h={40}
              color="var(--accent-color)"
              disabled={name.trim().length === 0}>
              Launch Workspace
            </Button>
          </Flex>
        </Stack>
      </form>
    </Stack>
  );
}

function getCurrencyInfo(currency: string, locale = navigator.language) {
  const name = new Intl.DisplayNames([locale], { type: 'currency' }).of(currency);

  const symbol = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    currencyDisplay: 'symbol'
  })
    .formatToParts(0)
    .find((p) => p.type === 'currency')?.value;

  return { currency, name, symbol };
}
