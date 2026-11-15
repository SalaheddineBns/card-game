import { HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PlayerProfileDto, PlayersService, UpdateProfileRequest } from '../../core/api';
import { SKIP_ERROR_TOAST } from '../../core/http/skip-error-toast';

/** The form shows these errors itself. */
const FORM_REQUEST = { context: new HttpContext().set(SKIP_ERROR_TOAST, true) };

/** The logged-in player's profile (rows 5-6). Provided by the profile route. */
@Injectable()
export class ProfileStore {
  private readonly api = inject(PlayersService);

  private readonly _profile = signal<PlayerProfileDto | null>(null);
  private readonly _loadFailed = signal(false);

  readonly profile = this._profile.asReadonly();
  readonly loadFailed = this._loadFailed.asReadonly();

  /** `GET /api/players/me`. Errors are toasted by the interceptor. */
  async load(): Promise<void> {
    this._loadFailed.set(false);
    try {
      this._profile.set(await firstValueFrom(this.api.getMyProfile()));
    } catch {
      this._loadFailed.set(true);
    }
  }

  /** `PUT /api/players/me`. Rejects with the `HttpErrorResponse` so the form can map field errors. */
  async update(request: UpdateProfileRequest): Promise<PlayerProfileDto> {
    const profile = await firstValueFrom(
      this.api.updateMyProfile({ updateProfileRequest: request }, 'body', false, FORM_REQUEST),
    );
    this._profile.set(profile);
    return profile;
  }
}
