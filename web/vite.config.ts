import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/*.png'],
      workbox: {
        // OpenCV.js and the Tesseract WASM core/traineddata are well over
        // Workbox's 2MB default - raise the cap so the whole scanning
        // pipeline is precached for true offline use after first load.
        maximumFileSizeToCacheInBytes: 30 * 1024 * 1024,
        globPatterns: ['**/*.{js,css,html,ico,png,svg,wasm,gz}'],
      },
      manifest: {
        name: 'Production Board Scanner',
        short_name: 'Board Scanner',
        description: 'Scan a production whiteboard and generate the daily status email - entirely on-device.',
        theme_color: '#1a1a1e',
        background_color: '#1a1a1e',
        display: 'standalone',
        orientation: 'any',
        start_url: '/',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
    }),
  ],
  build: {
    // The OpenCV.js chunk (~14MB unminified WASM+JS) is only ever loaded
    // via dynamic import when a scan starts, so it's fine for it to be
    // large - it isn't part of the initial page load.
    chunkSizeWarningLimit: 16000,
  },
});
