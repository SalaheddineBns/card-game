import { Injectable, signal } from '@angular/core';

const SAMPLES = 5;

/**
 * Estimates the server clock from the `sentAt` of received events and the `serverTime` of a
 * game snapshot (offset = serverTime - Date.now() on receipt, docs/realtime-protocol.md), so countdowns to server
 * instants (`roundEndsAt`, `expiresAt`) don't depend on the player's clock being right.
 * Network latency only makes a sample look older, so the largest recent offset is the best one.
 */
@Injectable({ providedIn: 'root' })
export class ServerClock {
  private samples: number[] = [];
  private readonly _offsetMs = signal(0);

  /** Server time minus local time, in ms. */
  readonly offsetMs = this._offsetMs.asReadonly();

  /** One sample: a server instant (`sentAt`, `serverTime`) and when it was received. */
  observe(serverInstant: string, receivedAt = Date.now()): void {
    const sent = Date.parse(serverInstant);
    if (Number.isNaN(sent)) {
      return;
    }
    this.samples = [...this.samples.slice(-(SAMPLES - 1)), sent - receivedAt];
    this._offsetMs.set(Math.max(...this.samples));
  }

  now(): number {
    return Date.now() + this._offsetMs();
  }
}
