import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { DesktopLanding } from './desktop-landing';
import { MobileLanding } from './mobile-landing';
import { useLandingView } from './use-landing-view';

export function LandingPage() {
  const isMobile = useIsMobile();
  const model = useLandingView();

  if (isMobile) {
    return <MobileLanding model={model} />;
  }

  return <DesktopLanding model={model} />;
}
