import { HttpContextToken } from '@angular/common/http';

/**
 * Set to `true` on a request whose errors the caller shows itself
 * (e.g. form field errors from a ProblemDetail), to skip the global toast.
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);
