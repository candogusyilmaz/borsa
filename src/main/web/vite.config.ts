import tanstackRouter from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const ReactCompilerConfig = {
  /* ... */
};

export default defineConfig({
  base: '/',
  plugins: [
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler', ReactCompilerConfig]]
      }
    })
  ],
  resolve: {
    alias: {
      '~/': '/src/',
      '@tabler/icons-react': '@tabler/icons-react/dist/esm/icons/index.mjs',
      '@mantine/core': '/src/lib/mantine/core.tsx',
      '@mantine/hooks': '/src/lib/mantine/hooks.ts',
      '@mantine/form': '/src/lib/mantine/form.ts',
      '@mantine/dates': '/src/lib/mantine/dates.tsx',
      '@mantine/charts': '/src/lib/mantine/charts.tsx',
      '@mantine/dropzone': '/src/lib/mantine/dropzone.tsx',
      '@mantine/notifications': '/src/lib/mantine/notifications.tsx'
    }
  }
});
