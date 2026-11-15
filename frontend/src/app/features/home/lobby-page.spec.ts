import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { render, screen, waitFor, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import {
  GamesService,
  InvitationDto,
  InvitationDtoStatusEnum as InvitationStatus,
  InvitationsService,
  OnlinePlayerDto,
  OnlinePlayerDtoStatusEnum as PlayerStatus,
  PlayersService,
} from '../../core/api';
import { ALICE, provideAuthApi, signIn } from '../../../testing/auth';
import {
  FakeRealtime,
  SENT_AT,
  fakeRealtime,
  provideFakeRealtime,
} from '../../../testing/realtime';
import { LobbyPage } from './lobby-page';

const ONLINE: OnlinePlayerDto[] = [
  { username: 'bob', status: PlayerStatus.ONLINE },
  { username: 'carol', status: PlayerStatus.IN_GAME },
];

function invitationDto(overrides: Partial<InvitationDto> = {}): InvitationDto {
  return {
    id: 12,
    inviter: 'alice',
    invitees: ['bob'],
    accepted: [],
    settings: { totalRounds: 3, roundSeconds: 10 },
    status: InvitationStatus.PENDING,
    declinedBy: null,
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    gameId: null,
    ...overrides,
  };
}

let realtime: FakeRealtime;
let invitationsApi: {
  createInvitation: ReturnType<typeof vi.fn>;
  acceptInvitation: ReturnType<typeof vi.fn>;
  declineInvitation: ReturnType<typeof vi.fn>;
  getPendingInvitations: ReturnType<typeof vi.fn>;
};

async function renderLobby() {
  realtime = fakeRealtime();
  invitationsApi = {
    createInvitation: vi.fn(() => of(invitationDto())),
    acceptInvitation: vi.fn(() =>
      of(
        invitationDto({
          id: 7,
          inviter: 'bob',
          invitees: ['alice'],
          accepted: ['alice'],
          status: InvitationStatus.ACCEPTED,
        }),
      ),
    ),
    declineInvitation: vi.fn(() => of(invitationDto({ status: InvitationStatus.DECLINED }))),
    getPendingInvitations: vi.fn(() => of([])),
  };
  const result = await render(LobbyPage, {
    providers: [
      provideRouter([{ path: '**', children: [] }]),
      MessageService,
      provideAuthApi(ALICE),
      provideFakeRealtime(realtime),
      { provide: PlayersService, useValue: { getOnlinePlayers: () => of(ONLINE) } },
      { provide: InvitationsService, useValue: invitationsApi },
      { provide: GamesService, useValue: { getCurrentGame: () => of(null) } },
    ],
  });
  await signIn();
  result.fixture.detectChanges();
  return result;
}

describe('LobbyPage', () => {
  it('lists online players with their status and follows presence events', async () => {
    await renderLobby();

    const list = await screen.findByRole('list', { name: 'Joueurs connectés' });
    expect(within(list).getByText('bob')).toBeInTheDocument();
    expect(within(list).getByText('En partie')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'carol' })).toBeDisabled();

    realtime.presence$.next({
      type: 'PlayerOnline',
      username: 'dave',
      status: 'ONLINE',
      sentAt: SENT_AT,
    });
    realtime.presence$.next({ type: 'PlayerOffline', username: 'bob', sentAt: SENT_AT });

    expect(await within(list).findByText('dave')).toBeInTheDocument();
    await waitFor(() => expect(within(list).queryByText('bob')).toBeNull());
  });

  it('invites the selected players with the chosen settings', async () => {
    await renderLobby();
    const user = userEvent.setup();
    const send = screen.getByRole('button', { name: "Envoyer l'invitation" });
    expect(send).toBeDisabled();

    await user.click(await screen.findByRole('checkbox', { name: 'bob' }));
    const seconds = screen.getByLabelText('Secondes par manche');
    await user.clear(seconds);
    await user.type(seconds, '30');
    await user.click(send);

    expect(invitationsApi.createInvitation).toHaveBeenCalledWith({
      createInvitationRequest: {
        invitees: ['bob'],
        settings: { totalRounds: 3, roundSeconds: 30 },
      },
    });
    expect(await screen.findByText('Invitation envoyée')).toBeInTheDocument();
  });

  it('shows an incoming invitation and accepts it', async () => {
    await renderLobby();
    const user = userEvent.setup();

    realtime.invitations$.next({
      type: 'InvitationReceived',
      invitationId: 7,
      inviter: 'bob',
      invitees: ['alice'],
      settings: { totalRounds: 1, roundSeconds: 5 },
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      sentAt: new Date().toISOString(),
    });

    const dialog = await screen.findByRole('dialog', { name: 'Invitation de bob' });
    expect(within(dialog).getByText(/s restantes/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Accepter' }));

    expect(invitationsApi.acceptInvitation).toHaveBeenCalledWith({ id: 7 });
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  it('closes the dialog when the invitation is cancelled by another player', async () => {
    await renderLobby();
    realtime.invitations$.next({
      type: 'InvitationReceived',
      invitationId: 7,
      inviter: 'bob',
      invitees: ['alice', 'carol'],
      settings: { totalRounds: 3, roundSeconds: 10 },
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      sentAt: SENT_AT,
    });
    await screen.findByRole('dialog');

    realtime.invitations$.next({
      type: 'InvitationUpdated',
      invitationId: 7,
      status: 'DECLINED',
      accepted: [],
      declinedBy: 'carol',
      sentAt: SENT_AT,
    });

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  it('goes to the game when it starts', async () => {
    await renderLobby();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    realtime.games$.next({ type: 'GameStarted', gameId: 34, sentAt: SENT_AT });

    expect(navigate).toHaveBeenCalledWith(['/games', 34]);
  });
});
