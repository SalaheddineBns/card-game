import { Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, ControlEvent, FormGroup } from '@angular/forms';
import { merge } from 'rxjs';
import { FieldViolation } from '../../core/http/problem-detail';

/** Validation error key for a message sent by the server. Cleared by the next local validation. */
export const SERVER_ERROR = 'server';

/** Validation error key → French message, for one control. */
export type ErrorMessages = Readonly<Record<string, string>>;

/** Marks a control invalid with a server message and shows it straight away. */
export function setServerError(control: AbstractControl, message: string): void {
  control.setErrors({ ...control.errors, [SERVER_ERROR]: message });
  control.markAsTouched();
}

/**
 * Puts each server field violation on the control of the same name.
 * Returns the violations that match no control (to show at form level).
 */
export function applyFieldViolations(
  form: FormGroup,
  violations: readonly FieldViolation[],
): FieldViolation[] {
  return violations.filter((violation) => {
    const control = form.get(violation.field);
    if (!control) {
      return true;
    }
    setServerError(control, violation.message);
    return false;
  });
}

/**
 * The message to show under a control: none until the player has left it (or submitted),
 * then the server message first, otherwise the first failing rule in `messages` order.
 */
export function errorMessage(control: AbstractControl, messages: ErrorMessages): string | null {
  const errors = control.errors;
  if (!errors || !control.touched) {
    return null;
  }
  const server: unknown = errors[SERVER_ERROR];
  if (typeof server === 'string') {
    return server;
  }
  const key = Object.keys(messages).find((k) => k in errors);
  return key ? messages[key] : null;
}

/**
 * A signal that changes on every value, status, touched or pristine change of the group or any of
 * its direct controls. Read it in a `computed()` to derive error messages. The group's own `events`
 * are not enough: a parent only reports "touched" the first time any child is touched.
 * Call in an injection context (e.g. a field initializer).
 */
export function formEvents(form: FormGroup): Signal<ControlEvent | undefined> {
  return toSignal(merge(form.events, ...Object.values(form.controls).map((c) => c.events)));
}
