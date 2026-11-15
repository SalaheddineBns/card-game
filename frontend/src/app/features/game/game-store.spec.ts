import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, of, throwError } from 'rxjs';
import {
  CardDtoColorEnum,
  CardDtoSymbolEnum,
  GameSnapshotDto,
  GameSnapshotDtoPhaseEnum,
  GamesService,
} from '../../core/api';
import { ALICE, httpError, provideAuthApi } from '../../../testing/auth';
import { fakeRealtime, provideFakeRealtime } from '../../../testing/realtime';
import { GameStore } from './game-store';

const T0 = Date.parse('2026-11-16T10:00:00Z');

/** Round `round` of 3, ending `endsInMs` after the (fake) current time. */
function snapshot(round: number, endsInMs: number): GameSnapshotDto {
  const now = Date.now();
  return {
    gameId: 34,
    players: ['alice', 'bob'],
    settings: { totalRounds: 3, roundSeconds: 10 },
    phase: GameSnapshotDtoPhaseEnum.IN_PROGRESS,
    round,
    roundStartedAt: new Date(now + endsInMs - 10_000).toISOString(),
    roundEndsAt: new Date(now + endsInMs).toISOString(),
    scores: [
      { player: 'alice', score: 0 },
      { player: 'bob', score: 0 },
    ],
    deal: {
      dealVersion: 1,
      playerCards: [],
      centerCard: { symbol: CardDtoSymbolEnum.A, color: CardDtoColorEnum.RED },
    },
    serverTime: new Date(now).toISOString(),
  };
}

function setup(...answers: (() => Observable<GameSnapshotDto>)[]) {
  const getGame = vi.fn(() => (answers.shift() ?? answers.at(-1) ?? (() => of(snapshot(3, 0))))());
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      MessageService,
      provideAuthApi(ALICE),
      provideFakeRealtime(fakeRealtime()),
      { provide: GamesService, useValue: { getGame } },
      GameStore,
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const add = vi.spyOn(TestBed.inject(MessageService), 'add');
  TestBed.inject(GameStore).open(34);
  return { getGame, navigate, add };
}

/** Lets the snapshot promise settle (microtasks are not faked). */
async function flush(): Promise<void> {
  await vi.advanceTimersByTimeAsync(0);
}

describe('GameStore end-of-game watchdog (R2)', () => {
  beforeEach(() => {
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'],
    });
    vi.setSystemTime(T0);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });

  it('reloads 2 s after the last round ends and opens the result page on 409', async () => {
    const { getGame, navigate } = setup(
      () => of(snapshot(3, 5_000)),
      () => throwError(() => httpError(409, { title: 'Partie terminée', status: 409 })),
    );
    await flush();
    expect(getGame).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(6_999);
    expect(getGame).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(1);
    expect(getGame).toHaveBeenCalledTimes(2);
    expect(navigate).toHaveBeenCalledWith(['/games', 34, 'result'], { replaceUrl: true });
  });

  it('shows the detail and goes to the lobby on 410', async () => {
    const { navigate, add } = setup(
      () => of(snapshot(3, 1_000)),
      () =>
        throwError(() =>
          httpError(410, {
            title: 'Partie abandonnée',
            status: 410,
            detail: 'Le serveur a redémarré.',
          }),
        ),
    );
    await flush();

    await vi.advanceTimersByTimeAsync(3_000);

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ detail: 'Le serveur a redémarré.' }),
    );
    expect(navigate).toHaveBeenCalledWith(['/lobby']);
  });

  it('keeps going and re-arms when the game is still live', async () => {
    const { getGame, navigate } = setup(
      () => of(snapshot(3, 1_000)),
      () => of(snapshot(3, -2_000)), // overdue but still running on the server
      () => throwError(() => httpError(409, { title: 'Partie terminée', status: 409 })),
    );
    await flush();

    await vi.advanceTimersByTimeAsync(3_000);
    expect(getGame).toHaveBeenCalledTimes(2);
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(GameStore).game()).not.toBeNull();

    await vi.advanceTimersByTimeAsync(2_000);
    expect(getGame).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenCalledWith(['/games', 34, 'result'], { replaceUrl: true });
  });

  it('does not arm before the last round', async () => {
    const { getGame } = setup(() => of(snapshot(1, 1_000)));
    await flush();

    await vi.advanceTimersByTimeAsync(60_000);

    expect(getGame).toHaveBeenCalledTimes(1);
  });

  it('uses the server clock from the snapshot', async () => {
    // The server is 30 s ahead: its round ends in 5 s of server time, so the reload comes after 7 s.
    const ahead = snapshot(3, 30_000 + 5_000);
    ahead.serverTime = new Date(Date.now() + 30_000).toISOString();
    const { getGame } = setup(() => of(ahead));
    await flush();

    await vi.advanceTimersByTimeAsync(7_000);

    expect(getGame).toHaveBeenCalledTimes(2);
  });

  it('cancels the timer when the page is destroyed', async () => {
    const { getGame } = setup(() => of(snapshot(3, 1_000)));
    await flush();

    TestBed.resetTestingModule();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(getGame).toHaveBeenCalledTimes(1);
  });
});
