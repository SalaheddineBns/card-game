import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { interval, map } from 'rxjs';
import { ServerClock } from '../../core/realtime/server-clock';

/**
 * Current server time (ms), refreshed every `periodMs`. For countdowns to server instants.
 * Call in an injection context; stops with the injector.
 */
export function injectServerNow(periodMs = 250): Signal<number> {
  const clock = inject(ServerClock);
  return toSignal(interval(periodMs).pipe(map(() => clock.now())), {
    initialValue: clock.now(),
  });
}

/** Whole seconds left until `instant` (ISO), never negative. */
export function secondsUntil(instant: string, now: number): number {
  return Math.max(0, Math.ceil((Date.parse(instant) - now) / 1000));
}
