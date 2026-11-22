import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/e2e',
  reporter: [['list'], ['junit', { outputFile: './test-results/playwright.xml' }]],
  workers: 1,
  timeout: 60_000,
  globalTimeout: 300_000,
  use: {
    baseURL: 'http://127.0.0.1:14200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      name: 'API',
      cwd: '..',
      command: 'java -jar target/locadora-livros-0.1.0-SNAPSHOT.jar',
      url: 'http://127.0.0.1:18080/api/v1/status',
      env: {
        SERVER_PORT: '18080',
        DB_PORT: process.env.E2E_DB_PORT ?? '15432',
        POSTGRES_DB: process.env.E2E_POSTGRES_DB ?? 'locadora_e2e',
        POSTGRES_USER: process.env.E2E_POSTGRES_USER ?? 'locadora_e2e',
        POSTGRES_PASSWORD: process.env.E2E_POSTGRES_PASSWORD ?? 'BancoFicticioE2E',
        APP_USERNAME: 'operador_e2e',
        APP_PASSWORD: 'OperadorFicticioE2E',
      },
      timeout: 90_000,
      reuseExistingServer: false,
    },
    {
      name: 'Angular',
      command: 'ng serve --host 127.0.0.1 --port 14200 --proxy-config e2e/proxy.conf.json',
      url: 'http://127.0.0.1:14200/entrar',
      timeout: 90_000,
      reuseExistingServer: false,
    },
  ],
});
