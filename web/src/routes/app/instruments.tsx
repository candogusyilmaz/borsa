import { Anchor, Box, Group } from '@mantine/core';
import { ArrowLeftIcon } from '@phosphor-icons/react';
import { createFileRoute, Link } from '@tanstack/react-router';
import { InstrumentDetailOverlay, InstrumentList, ManualInstrumentCreateOverlay } from '@/features/instrument';
import { createSeoMeta } from '@/shared/utils/seo';

export const Route = createFileRoute('/app/instruments')({
  head: () => {
    const seo = createSeoMeta({
      title: 'Financial Instruments & Reference Catalog',
      path: '/app/instruments',
      noIndex: true
    });
    return {
      meta: seo.meta,
      links: seo.links,
      scripts: seo.scripts
    };
  },
  component: InstrumentsRouteComponent
});

function InstrumentsRouteComponent() {
  function handleSelectInstrument(instrumentId: string) {
    InstrumentDetailOverlay.open({ instrumentId });
  }

  function handleOpenCreate() {
    ManualInstrumentCreateOverlay.open({});
  }

  return (
    <Box>
      <Box mb="md">
        <Anchor
          component={Link}
          to="/app"
          size="sm"
          c="dimmed"
          style={{ display: 'inline-flex', alignItems: 'center', gap: 6, minHeight: 44 }}>
          <Group gap={6}>
            <ArrowLeftIcon size={16} />
            <span>Back to Dashboard</span>
          </Group>
        </Anchor>
      </Box>

      <InstrumentList onSelectInstrument={handleSelectInstrument} onOpenCreate={handleOpenCreate} />
    </Box>
  );
}
