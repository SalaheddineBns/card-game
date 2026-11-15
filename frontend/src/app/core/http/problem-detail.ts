/** RFC 9457 problem detail, as returned by Spring's `ProblemDetail`. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Spring validation errors, if the backend adds them as an extension property. */
  errors?: Record<string, string[] | string>;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate['title'] === 'string' || typeof candidate['detail'] === 'string';
}
