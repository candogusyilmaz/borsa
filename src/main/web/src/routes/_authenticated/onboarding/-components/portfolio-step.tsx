import { Button, Group, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconCheck, IconFolder } from '@tabler/icons-react';
import type React from 'react';
import { useState } from 'react';
import type { OnboardingRequest } from '../route';

interface PortfolioStepProps {
  onNext: () => void;
  onboardingRequest: OnboardingRequest;
  setOnboardingRequest: React.Dispatch<React.SetStateAction<OnboardingRequest>>;
}

const COLORS = [
  { name: 'Blue', hex: '#3b82f6' },
  { name: 'Purple', hex: '#a855f7' },
  { name: 'Emerald', hex: '#10b981' },
  { name: 'Amber', hex: '#f59e0b' },
  { name: 'Rose', hex: '#f43f5e' },
  { name: 'Slate', hex: '#475569' }
] as const;

export function PortfolioStep({ onboardingRequest, setOnboardingRequest, onNext }: PortfolioStepProps) {
  const [name, setName] = useState<string>(onboardingRequest.portfolio.name);
  const [color, setColor] = useState<string>(onboardingRequest.portfolio.color);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    setOnboardingRequest((prev) => ({
      ...prev,
      portfolio: {
        name: name.trim(),
        color,
        trades: [],
        currency: prev.portfolio.currency ?? onboardingRequest.portfolio.currency ?? 'TRY'
      }
    }));

    onNext();
  };

  return (
    <Stack>
      <Stack gap={2} ta="center">
        <Title order={2}>Portfolio Settings</Title>
        <Text c="dimmed" fz="sm">
          Give your new portfolio a name and a visual brand.
        </Text>
      </Stack>

      <form onSubmit={handleSubmit} className="space-y-6">
        <Stack gap={35}>
          <TextInput
            name="name"
            label="PORTFOLIO NAME"
            placeholder="e.g. Tech Fund"
            leftSection={<IconFolder size={18} />}
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

          <Stack gap={10}>
            <Group justify="space-between">
              <Text fz={12} lts={-0.08} ml={2} fw={600} c="var(--text-muted)">
                THEME COLOR
              </Text>
              <Text fz={10} c="var(--accent-color)" fw={700}>
                Visual Identity
              </Text>
            </Group>
            <Group grow>
              {COLORS.map((s) => {
                const isSelected = color === s.hex;

                return (
                  <button
                    type="button"
                    key={s.hex}
                    onClick={() => setColor(s.hex)}
                    data-active={isSelected}
                    style={{
                      backgroundColor: s.hex,
                      height: '40px',
                      width: '40px',
                      borderRadius: '12px',
                      transition: 'all 0.2s ease-in-out',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      border: 'none',
                      cursor: 'pointer',
                      boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                      position: 'relative',
                      overflow: 'hidden',
                      padding: 0
                    }}>
                    {isSelected && (
                      <div
                        style={{
                          backgroundColor: 'rgba(255, 255, 255, 0.2)',
                          position: 'absolute',
                          inset: 0,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          animation: 'zoomIn 0.2s ease-out'
                        }}>
                        <IconCheck size={18} color="white" strokeWidth={3} />
                      </div>
                    )}
                  </button>
                );
              })}
            </Group>
          </Stack>

          <Button
            type="submit"
            tt="uppercase"
            lts={1}
            loading={false}
            h={40}
            color="var(--accent-color)"
            disabled={name.trim().length === 0 || color.trim().length === 0}>
            Define Portfolio
          </Button>
        </Stack>
      </form>
    </Stack>
  );
}
