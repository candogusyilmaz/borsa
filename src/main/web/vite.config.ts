import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { TanStackRouterVite } from '@tanstack/router-plugin/vite';

export default defineConfig({
  plugins: [TanStackRouterVite({ autoCodeSplitting: true }), react()],
  resolve: {
    alias: {
      '~/': '/src/',
      '@tabler/icons-react': '@tabler/icons-react/dist/esm/icons/index.mjs'
    }
  },
  build: {
    outDir: '../resources/static/',
    emptyOutDir: true
  }
});
