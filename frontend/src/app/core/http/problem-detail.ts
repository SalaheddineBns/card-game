import { HttpErrorResponse } from '@angular/common/http';

import { FieldViolation, Problem } from '../api';

export type { FieldViolation } from '../api';

/**
 * RFC 9457 problem detail from the backend's `GlobalExceptionHandler` (French `title`/`detail`),
 * generated as `Problem`; `errors` (FieldViolation[]) is only set on 400 validation errors.
 */
export type ProblemDetail = Problem;

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['title'] === 'string' || typeof candidate['detail'] === 'string';
}

/** The ProblemDetail carried by an HTTP error, if any. */
export function problemOf(error: unknown): ProblemDetail | null {
  return error instanceof HttpErrorResponse && isProblemDetail(error.error) ? error.error : null;
}

/** The well-formed field violations of a ProblemDetail (empty when there are none). */
export function fieldViolations(problem: ProblemDetail | null): FieldViolation[] {
  const errors: unknown = problem?.errors;
  if (!Array.isArray(errors)) {
    return [];
  }
  return errors.filter(
    (e: unknown): e is FieldViolation =>
      typeof e === 'object' &&
      e !== null &&
      typeof (e as Record<string, unknown>)['field'] === 'string' &&
      typeof (e as Record<string, unknown>)['message'] === 'string',
  );
}
