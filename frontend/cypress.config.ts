import { defineConfig } from 'cypress';

export default defineConfig({
  expose: {
    API_BASE_URL: process.env.CYPRESS_API_BASE_URL || 'http://localhost:8080/api',
    // US-01: credenciais do Admin criado pelo AdminSeed (mesmos valores padrão do docker-compose.yml).
    ADMIN_EMAIL: process.env.CYPRESS_ADMIN_EMAIL || 'admin@exemplo.com',
    ADMIN_PASSWORD: process.env.CYPRESS_ADMIN_PASSWORD || 'Admin@12345',
  },
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:5173',
    supportFile: 'cypress/support/e2e.ts',
    video: false,
    screenshotOnRunFailure: false,
  },
});
