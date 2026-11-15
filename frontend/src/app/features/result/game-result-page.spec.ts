import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { GameResultDto, GamesService } from '../../core/api';
import { ALICE, provideAuthApi } from '../../../testing/auth';
import { GameResultPage } from './game-result-page';

async function renderResult(result: GameResultDto) {
  return render(GameResultPage, {
    inputs: { id: 34 },
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      MessageService,
      provideAuthApi(ALICE),
      { provide: GamesService, useValue: { getGameResult: () => of(result) } },
    ],
  });
}

describe('GameResultPage', () => {
  it('shows the winner, the loser and every score', async () => {
    await renderResult({
      gameId: 34,
      draw: false,
      winner: 'alice',
      loser: 'carol',
      leaders: ['alice'],
      topScore: 3,
      trailers: ['carol'],
      bottomScore: 1,
      scores: [
        { player: 'alice', score: 3 },
        { player: 'bob', score: 2 },
        { player: 'carol', score: 1 },
      ],
    });

    expect(await screen.findByText('Gagnant :')).toBeInTheDocument();
    expect(screen.getByText('Gagnant :').parentElement).toHaveTextContent('alice (Score : 3)');
    expect(screen.getByText('Perdant :').parentElement).toHaveTextContent('carol (Score : 1)');
    expect(screen.getAllByRole('row')).toHaveLength(4);
    expect(screen.getByRole('link', { name: 'Rejouer' })).toHaveAttribute('href', '/lobby');
  });

  it('shows a draw without a loser line when every score is equal', async () => {
    await renderResult({
      gameId: 34,
      draw: true,
      leaders: ['alice', 'bob'],
      topScore: 0,
      trailers: [],
      bottomScore: 0,
      scores: [
        { player: 'alice', score: 0 },
        { player: 'bob', score: 0 },
      ],
    });

    expect(await screen.findByText('Égalité')).toBeInTheDocument();
    expect(screen.getByText(/Ex aequo : alice, bob/)).toBeInTheDocument();
    expect(screen.queryByText('Gagnant :')).toBeNull();
    expect(screen.queryByText('Perdant :')).toBeNull();
  });

  it('keeps the loser line in a draw for first place with a single last player', async () => {
    await renderResult({
      gameId: 34,
      draw: true,
      loser: 'carol',
      leaders: ['alice', 'bob'],
      topScore: 2,
      trailers: ['carol'],
      bottomScore: 0,
      scores: [
        { player: 'alice', score: 2 },
        { player: 'bob', score: 2 },
        { player: 'carol', score: 0 },
      ],
    });

    expect(await screen.findByText('Égalité')).toBeInTheDocument();
    expect(screen.getByText('Perdant :').parentElement).toHaveTextContent('carol (Score : 0)');
  });
});
