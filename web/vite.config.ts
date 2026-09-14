import fs from 'node:fs';
import path from 'node:path';
import tanstackRouter from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import { type Plugin, loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

function sitemapPlugin(): Plugin {
  let root = process.cwd();
  let outDir = 'dist';

  return {
    name: 'generate-sitemap',
    apply: 'build',
    configResolved(config) {
      root = config.root;
      outDir = config.build.outDir;
    },
    closeBundle() {
      const env = loadEnv('production', root, '');
      const rawUrl = env.VITE_APP_URL || process.env.VITE_APP_URL || 'http://localhost:5173';
      const baseUrl = rawUrl.replace(/\/+$/, '');
      const lastMod = new Date().toISOString().split('T')[0];

      const routes = [
        { path: '/', priority: '1.0', changefreq: 'daily' },
        { path: '/login', priority: '0.8', changefreq: 'monthly' },
        { path: '/register', priority: '0.8', changefreq: 'monthly' }
      ];

      const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${routes
  .map(
    (r) => `  <url>
    <loc>${baseUrl}${r.path}</loc>
    <lastmod>${lastMod}</lastmod>
    <changefreq>${r.changefreq}</changefreq>
    <priority>${r.priority}</priority>
  </url>`
  )
  .join('\n')}
</urlset>
`;

      const resolvedOutDir = path.isAbsolute(outDir) ? outDir : path.resolve(root, outDir);
      if (!fs.existsSync(resolvedOutDir)) {
        fs.mkdirSync(resolvedOutDir, { recursive: true });
      }
      fs.writeFileSync(path.join(resolvedOutDir, 'sitemap.xml'), sitemap, 'utf-8');

      const robotsPath = path.join(resolvedOutDir, 'robots.txt');
      if (fs.existsSync(robotsPath)) {
        const robotsContent = fs.readFileSync(robotsPath, 'utf-8');
        const updatedRobots = robotsContent.replace(/Sitemap:\s*(\S+)/g, `Sitemap: ${baseUrl}/sitemap.xml`);
        fs.writeFileSync(robotsPath, updatedRobots, 'utf-8');
      }
    }
  };
}

export default defineConfig({
  plugins: [
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react({
      compiler: {
        target: '19'
      }
    }),
    sitemapPlugin()
  ],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src')
    }
  },
  server: {
    host: true,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'node',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}']
  }
});
