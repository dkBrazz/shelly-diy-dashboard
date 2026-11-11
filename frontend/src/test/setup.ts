import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Testing Library only auto-registers cleanup when `afterEach` is a global; we keep
// Vitest globals off and import the API explicitly, so register it here instead.
afterEach(() => {
  cleanup();
});
