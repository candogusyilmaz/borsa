export function isStandaloneApp() {
  if (typeof window === 'undefined') {
    return false;
  }

  const isAppleStandalone = (window.navigator as Navigator & { standalone?: boolean }).standalone === true;

  return window.matchMedia('(display-mode: standalone)').matches || isAppleStandalone;
}
