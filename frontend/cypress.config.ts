import { defineConfig } from 'cypress';

export default defineConfig({
  expose: {
    API_BASE_URL: process.env.CYPRESS_API_BASE_URL || 'http://localhost:8080/api',
  },
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:5173',
    supportFile: false,
    video: false,
    screenshotOnRunFailure: false,
  },
});
