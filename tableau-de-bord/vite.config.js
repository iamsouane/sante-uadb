import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: { port: 4173, proxy: { '/api/supervision': 'http://localhost:8090' } },
  test: { environment: 'jsdom' },
});
