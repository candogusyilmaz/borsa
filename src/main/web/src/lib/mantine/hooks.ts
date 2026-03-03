import { useEffect, useState } from 'react';

export function useDisclosure(initialState = false) {
  const [opened, setOpened] = useState(initialState);
  return [opened, { open: () => setOpened(true), close: () => setOpened(false), toggle: () => setOpened((v) => !v) }] as const;
}

export function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => (typeof window !== 'undefined' ? window.matchMedia(query).matches : false));

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const media = window.matchMedia(query);
    const listener = (event: MediaQueryListEvent) => setMatches(event.matches);
    setMatches(media.matches);
    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, [query]);

  return matches;
}
