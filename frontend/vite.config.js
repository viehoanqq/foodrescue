import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  define: {
    'import.meta.env.VITE_GOOGLE_CLIENT_ID': JSON.stringify(loadEnv(mode, '..', 'GOOGLE_CLIENT_ID').GOOGLE_CLIENT_ID || ''),
  },
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.FOODRESCUE_API_GATEWAY || 'http://localhost:8080',
      '/uploads': process.env.FOODRESCUE_API_GATEWAY || 'http://localhost:8080',
    },
  },
}));
