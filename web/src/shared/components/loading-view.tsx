import { useLingui } from '@lingui/react/macro';
import { Center, Loader } from '@mantine/core';

export function LoadingView() {
  const { t } = useLingui();
  return (
    <Center h="100%" mih={100}>
      <Loader aria-label={t`Loading…`} />
    </Center>
  );
}
