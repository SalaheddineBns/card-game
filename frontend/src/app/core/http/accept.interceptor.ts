import { HttpInterceptorFn } from '@angular/common/http';

const PROBLEM_ONLY = 'application/problem+json';

/**
 * For operations with no success body (e.g. `POST /api/auth/logout` → 204) the generated client
 * sends `Accept: application/problem+json` only, and the backend answers 406 instead of 204.
 * Widen that header so the success response is acceptable too.
 * TODO(backend): remove once no-content endpoints ignore Accept (reported in the frontend hand-off).
 */
export const acceptInterceptor: HttpInterceptorFn = (req, next) =>
  next(
    req.headers.get('Accept') === PROBLEM_ONLY
      ? req.clone({ setHeaders: { Accept: `application/json, ${PROBLEM_ONLY}` } })
      : req,
  );
