// Retire the service worker installed by the previous vite-plugin-pwa build.
// Keep this file at /sw.js so returning clients can update the old registration.
self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      await self.registration.unregister();

      const cacheNames = await caches.keys();
      await Promise.all(cacheNames.map((cacheName) => caches.delete(cacheName)));

      const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      await Promise.all(
        clients.map((client) => client.navigate(client.url).catch(() => undefined))
      );
    })()
  );
});
