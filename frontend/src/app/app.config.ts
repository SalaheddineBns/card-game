import { registerLocaleData } from '@angular/common';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  effect,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { provideApi } from './core/api';
import { AuthStore } from './core/auth/auth-store';
import { acceptInterceptor } from './core/http/accept.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { RealtimeService } from './core/realtime/realtime.service';

registerLocaleData(localeFr);

/** Aura with an emerald primary (card-table green). */
const CardGamePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{emerald.50}',
      100: '{emerald.100}',
      200: '{emerald.200}',
      300: '{emerald.300}',
      400: '{emerald.400}',
      500: '{emerald.500}',
      600: '{emerald.600}',
      700: '{emerald.700}',
      800: '{emerald.800}',
      900: '{emerald.900}',
      950: '{emerald.950}',
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      // Spring Security's CookieCsrfTokenRepository names (also Angular's defaults, kept explicit).
      // Angular adds the header to same-origin, non-GET, relative-URL requests only.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([acceptInterceptor, errorInterceptor]),
    ),
    // Relative URLs (`/api/...`): same origin through the dev proxy, and Angular only adds the
    // X-XSRF-TOKEN header to relative URLs.
    provideApi(''),
    // Know who is logged in before the first navigation (also primes the XSRF-TOKEN cookie).
    provideAppInitializer(() => inject(AuthStore).restoreSession()),
    providePrimeNG({
      theme: {
        preset: CardGamePreset,
        options: {
          darkModeSelector: '.app-dark',
          cssLayer: { name: 'primeng', order: 'theme, base, primeng' },
        },
      },
      ripple: true,
    }),
    MessageService,
    { provide: LOCALE_ID, useValue: 'fr' },
    // One STOMP connection while a player is logged in.
    provideEnvironmentInitializer(() => {
      const auth = inject(AuthStore);
      const realtime = inject(RealtimeService);
      effect(() => {
        if (auth.isAuthenticated()) {
          realtime.connect();
        } else {
          void realtime.disconnect();
        }
      });
    }),
  ],
};
