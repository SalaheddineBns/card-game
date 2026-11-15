import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

const STORAGE_KEY = 'card-game.theme';
export const DARK_CLASS = 'app-dark';

/**
 * Dark mode: toggles `.app-dark` on <html> (PrimeNG `darkModeSelector` and Tailwind `dark:` variant).
 * The choice is stored in localStorage; the default follows `prefers-color-scheme`.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly _dark = signal(this.initialPreference());

  readonly dark = this._dark.asReadonly();

  constructor() {
    effect(() => {
      const dark = this._dark();
      this.document.documentElement.classList.toggle(DARK_CLASS, dark);
      this.store(dark);
    });
  }

  toggle(): void {
    this._dark.update((dark) => !dark);
  }

  private initialPreference(): boolean {
    const stored = this.read();
    if (stored !== null) {
      return stored === 'dark';
    }
    const view = this.document.defaultView;
    return typeof view?.matchMedia === 'function'
      ? view.matchMedia('(prefers-color-scheme: dark)').matches
      : false;
  }

  private read(): string | null {
    try {
      return this.document.defaultView?.localStorage.getItem(STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  private store(dark: boolean): void {
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
    } catch {
      // Storage unavailable (private mode): the preference just isn't remembered.
    }
  }
}
