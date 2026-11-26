import { lingui } from '@lingui/vite-plugin';
import tanstackRouter from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

const ReactCompilerConfig = {
  /* ... */
};

export default defineConfig({
  base: "/",
  plugins: [
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    lingui({ macroTransform: true }),
    react({
      babel: {
        plugins: [["babel-plugin-react-compiler", ReactCompilerConfig]],
      },
    }),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["assets/favicon.svg", "assets/favicon.png", "assets/apple-touch-icon.png", "assets/logo.png"],
      manifest: {
        name: "Canverse",
        short_name: "Canverse",
        description: "Personal portfolio and trade tracking across multiple portfolios and currencies.",
        theme_color: "#0D0D0F",
        background_color: "#0D0D0F",
        display: "standalone",
        orientation: "portrait",
        scope: "/",
        id: "/",
        start_url: "/dashboard",
        icons: [
          {
            src: "/assets/pwa-192x192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "/assets/pwa-512x512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "/assets/pwa-maskable-512x512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: "CacheFirst",
            options: {
              cacheName: "google-fonts-cache",
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: "CacheFirst",
            options: {
              cacheName: "gstatic-fonts-cache",
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      "~/": "/src/",
      "@tabler/icons-react": "@tabler/icons-react/dist/esm/icons/index.mjs",
    },
  },
});
