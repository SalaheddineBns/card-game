import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthStore } from './auth-store';

/** Lets authenticated players through; otherwise redirects to the login page with a return URL. */
export const authGuard: CanMatchFn = async (_route, segments) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.isAuthenticated() || (await auth.restoreSession())) {
    return true;
  }

  const returnUrl = '/' + segments.map((segment) => segment.path).join('/');
  return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl } });
};
