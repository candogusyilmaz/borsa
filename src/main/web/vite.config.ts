import tanstackRouter from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import {defineConfig} from 'vite';

const ReactCompilerConfig = {
    /* ... */
};

export default defineConfig({
    plugins: [
        tanstackRouter({target: 'react', autoCodeSplitting: true}),
        react({
            babel: {
                plugins: [['babel-plugin-react-compiler', ReactCompilerConfig]]
            }
        })
    ],
    resolve: {
        alias: {
            '~/': '/src/',
            '@tabler/icons-react': '@tabler/icons-react/dist/esm/icons/index.mjs'
        }
    }
});
