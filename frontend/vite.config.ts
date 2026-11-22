/// <reference types="vitest/config" />
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  plugins: [
    react(),
    tailwindcss(),
    // El React Compiler es lento: solo se aplica en build, no en el servidor de desarrollo.
    ...(command === 'build' ? [babel({ presets: [reactCompilerPreset()] })] : []),
  ],
  server: {
    // La API se sirve desde Spring Boot; el proxy evita configurar CORS en local.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    exclude: ['tests/e2e/**', 'node_modules/**'],
    coverage: {
      provider: 'v8',
      include: [
        'src/features/auth/auth-validation.ts',
        'src/features/auth/session-storage.ts',
        'src/features/restaurant/restaurant-validation.ts',
        'src/features/catalog/catalog-validation.ts',
        'src/features/cart/cart-validation.ts',
        'src/features/checkout/checkout-validation.ts',
        'src/features/restaurant/orders-logic.ts',
        'src/features/delivery/courier-logic.ts',
      ],
      reporter: ['text', 'html'],
      thresholds: { lines: 90 },
    },
  },
}))
