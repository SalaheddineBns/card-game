import { HttpErrorResponse } from '@angular/common/http';

/** One invalid request field, as listed in a 400 ProblemDetail's `errors`. */
export interface FieldViolation {
  field: string;
  message: string;
}

/**
 * RFC 9457 problem detail, as returned by the backend's `GlobalExceptionHandler`
 * (French `title`/`detail`). Hand-written because the generated `ProblemDetail` model
 * does not describe the `errors` extension.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Validation errors (400 only). */
  errors?: FieldViolation[];
}

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
