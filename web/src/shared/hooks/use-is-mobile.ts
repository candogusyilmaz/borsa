import { useMediaQuery } from '@mantine/hooks';
import { MOBILE_BREAKPOINT } from '~/theme/tokens';

export function useIsMobile() {
  return useMediaQuery(`(max-width: ${MOBILE_BREAKPOINT})`, false, { getInitialValueInEffect: false });
}
