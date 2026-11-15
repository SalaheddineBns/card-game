import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStore } from '../auth/auth-store';
import { NotificationService } from '../notification/notification.service';
import { isProblemDetail } from './problem-detail';
import { SKIP_ERROR_TOAST } from './skip-error-toast';

/** 401 on these is an expected answer handled by the caller, not an expired session. */
const AUTH_ENDPOINTS = ['/api/auth/me', '/api/auth/login'];

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.endsWith(path));

      if (error.status === 401 && !isAuthEndpoint) {
        auth.clear();
        notifications.warn('Session expirée', 'Veuillez vous reconnecter.');
        void router.navigate(['/auth/login'], {
          queryParams: { returnUrl: router.url },
        });
      } else if (!req.context.get(SKIP_ERROR_TOAST) && !isAuthEndpoint) {
        notifyError(notifications, error);
      }
      return throwError(() => error);
    }),
  );
};

function notifyError(notifications: NotificationService, error: HttpErrorResponse): void {
  if (error.status === 0) {
    notifications.error('Serveur injoignable', 'Vérifiez votre connexion et réessayez.');
  } else if (isProblemDetail(error.error)) {
    notifications.error(error.error.title ?? 'Erreur', error.error.detail);
  } else {
    notifications.error('Erreur', `Une erreur inattendue est survenue (${error.status}).`);
  }
}
