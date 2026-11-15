import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { render, screen, waitFor } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { Observable, Subject, of, throwError } from 'rxjs';
import { GameSnapshotDto, GameSnapshotDtoPhaseEnum, GamesService } from '../../core/api';
import { Deal, PlayerScore } from '../../core/realtime/protocol';
import { ALICE, httpError, provideAuthApi, signIn } from '../../../testing/auth';
import {
  FakeRealtime,
  SENT_AT,
  fakeRealtime,
  provideFakeRealtime,
} from '../../../testing/realtime';
import { GamePage } from './game-page';

const DEAL_1: Deal = {
  dealVersion: 1,
  playerCards: [
    { player: 'alice', symbol: 'B', color: 'blue' },
    { player: 'bob', symbol: 'K', color: 'red' },
  ],
  centerCard: { symbol: 'B', color: 'red' },
};

const DEAL_2: Deal = {
  dealVersion: 2,
  playerCards: [
    { player: 'alice', symbol: 'E', color: 'green' },
    { player: 'bob', symbol: 'A', color: 'blue' },
  ],
  centerCard: { symbol: 'A', color: 'green' },
};

const SCORES_1: PlayerScore[] = [
  { player: 'alice', score: 1 },
  { player: 'bob', score: 0 },
];

function snapshot(): GameSnapshotDto {
  return {
    gameId: 34,
    players: ['alice', 'bob'],
    settings: { totalRounds: 3, roundSeconds: 10 },
    phase: GameSnapshotDtoPhaseEnum.IN_PROGRESS,
    round: 1,
    roundEndsAt: new Date(Date.now() + 10_000).toISOString(),
    scores: [
      { player: 'alice', score: 0 },
      { player: 'bob', score: 0 },
    ],
    deal: DEAL_1,
  };
}

let realtime: FakeRealtime;

async function renderGame(getGame: () => Observable<GameSnapshotDto> = () => of(snapshot())) {
  realtime = fakeRealtime();
  const result = await render(GamePage, {
    inputs: { id: 34 },
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      MessageService,
      provideAuthApi(ALICE),
      provideFakeRealtime(realtime),
      { provide: GamesService, useValue: { getGame: vi.fn(getGame) } },
    ],
  });
  await signIn();
  result.fixture.detectChanges();
  return result;
}

describe('GamePage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('shows the round, the center card and every player with score and card', async () => {
    await renderGame();

    expect(await screen.findByText('Manche 1 / 3')).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: 'Carte B rouge' }).length).toBeGreaterThan(0);
    expect(screen.getByRole('img', { name: 'Carte B bleu' })).toBeInTheDocument();
    expect(screen.getByText('(vous)')).toBeInTheDocument();
    expect(realtime.watchGame).toHaveBeenCalledWith(34);
  });

  it('sends a claim with the current deal version and locks the buttons until the answer', async () => {
    await renderGame();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /Même couleur/ }));

    expect(realtime.sendClaim).toHaveBeenCalledWith(34, { claim: 'SAME_COLOR', dealVersion: 1 });
    expect(screen.getByRole('button', { name: /Même forme/ })).toBeDisabled();

    realtime.errors$.next({
      type: 'ClaimRejected',
      gameId: 34,
      reason: 'WRONG',
      currentDealVersion: 1,
      sentAt: SENT_AT,
    });

    expect(await screen.findByText(/Raté/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Même forme/ })).toBeEnabled();
  });

  it('claims with the keyboard shortcuts', async () => {
    await renderGame();
    const user = userEvent.setup();
    await screen.findByText('Manche 1 / 3');

    await user.keyboard('d');

    expect(realtime.sendClaim).toHaveBeenCalledWith(34, { claim: 'BOTH', dealVersion: 1 });
  });

  it('tells the player when the claim came too late', async () => {
    await renderGame();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /Les deux/ }));

    realtime.errors$.next({
      type: 'ClaimRejected',
      gameId: 34,
      reason: 'STALE',
      currentDealVersion: 2,
      sentAt: SENT_AT,
    });

    expect(await screen.findByText(/Trop tard/)).toBeInTheDocument();
  });

  it('subscribes before loading and ignores buffered events not newer than the snapshot', async () => {
    const response = new Subject<GameSnapshotDto>();
    await renderGame(() => response);

    // Arrive while the snapshot is loading: v1 is already in the snapshot, v2 is newer.
    const stale = {
      type: 'ScoresUpdated',
      gameId: 34,
      scoredBy: 'bob',
      deal: DEAL_1,
      sentAt: SENT_AT,
    } as const;
    realtime.game$.next({
      ...stale,
      scores: [
        { player: 'alice', score: 0 },
        { player: 'bob', score: 9 },
      ],
    });
    realtime.game$.next({
      type: 'ScoresUpdated',
      gameId: 34,
      scoredBy: 'alice',
      scores: SCORES_1,
      deal: DEAL_2,
      sentAt: SENT_AT,
    });
    response.next(snapshot());
    response.complete();

    expect(await screen.findByText('Bien joué ! +1 point.')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Carte A vert' })).toBeInTheDocument();
    expect(screen.queryByText('9')).toBeNull();
  });

  it('starts the next round and announces it', async () => {
    await renderGame();
    await screen.findByText('Manche 1 / 3');

    realtime.game$.next({
      type: 'RoundStarted',
      gameId: 34,
      round: 2,
      totalRounds: 3,
      roundEndsAt: new Date(Date.now() + 10_000).toISOString(),
      scores: SCORES_1,
      deal: DEAL_2,
      sentAt: SENT_AT,
    });

    expect(await screen.findByText('Manche 2 / 3')).toBeInTheDocument();
  });

  it('goes to the result page when the game finishes', async () => {
    await renderGame();
    await screen.findByText('Manche 1 / 3');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    realtime.game$.next({
      type: 'GameFinished',
      gameId: 34,
      draw: false,
      winner: 'alice',
      loser: 'bob',
      leaders: ['alice'],
      topScore: 1,
      trailers: ['bob'],
      bottomScore: 0,
      scores: SCORES_1,
      sentAt: SENT_AT,
    });

    expect(navigate).toHaveBeenCalledWith(['/games', 34, 'result'], { replaceUrl: true });
  });

  it('sends the player back to the lobby when the game was abandoned (410)', async () => {
    const add = vi.spyOn(MessageService.prototype, 'add');
    const navigate = vi.spyOn(Router.prototype, 'navigate').mockResolvedValue(true);

    await renderGame(() =>
      throwError(() =>
        httpError(410, {
          title: 'Partie abandonnée',
          status: 410,
          detail: 'Le serveur a redémarré.',
        }),
      ),
    );

    await waitFor(() => expect(navigate).toHaveBeenCalledWith(['/lobby']));
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ detail: 'Le serveur a redémarré.' }),
    );
  });

  it('opens the result page when the snapshot says the game is finished (409)', async () => {
    const navigate = vi.spyOn(Router.prototype, 'navigate').mockResolvedValue(true);

    await renderGame(() =>
      throwError(() => httpError(409, { title: 'Partie terminée', status: 409 })),
    );

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith(['/games', 34, 'result'], { replaceUrl: true }),
    );
  });
});
