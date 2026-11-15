import '@testing-library/jest-dom/vitest';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';

// Same locale data as app.config.ts (French UI, D21).
registerLocaleData(localeFr);
