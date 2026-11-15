import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, map, throwError } from 'rxjs';
import { GamesService } from '../../core/api';
import { problemOf } from '../../core/http/problem-detail';
import { SKIP_ERROR_TOAST } from '../../core/http/skip-error-toast';
import { NotificationService } from '../../core/notification/notification.service';
import { GameResultView, toGameResultView } from '../../shared/game/game-mappers';

const QUIET = { context: new HttpContext().set(SKIP_ERROR_TOAST, true) };

/** Loads a finished game's result (row 14) and handles the "not a result" answers by navigating. */
@Injectable({ providedIn: 'root' })
export class GameResultService {
  private readonly api = inject(GamesService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  /** Completes without a value when it navigated away (409 still running, 410/403/404). */
  load(gameId: number): Observable<GameResultView> {
    return this.api.getGameResult({ id: gameId }, 'body', false, QUIET).pipe(
      map(toGameResultView),
      catchError((error: unknown) => {
        const status = error instanceof HttpErrorResponse ? error.status : null;
        const problem = problemOf(error);
        if (status === 409) {
          void this.router.navigate(['/games', gameId], { replaceUrl: true });
          return EMPTY;
        }
        if (status === 410 || status === 403 || status === 404) {
          this.notifications.warn(
            problem?.title ?? 'Résultat indisponible',
            problem?.detail ?? "Le résultat de cette partie n'est pas disponible.",
          );
          void this.router.navigate(['/lobby']);
          return EMPTY;
        }
        return throwError(() => error);
      }),
    );
  }
}
