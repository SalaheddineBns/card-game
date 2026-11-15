import { LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { PlayerProfileDto, PlayersService } from '../../core/api';
import { ProfilePage } from './profile-page';
import { ProfileStore } from './profile-store';

const PROFILE: PlayerProfileDto = {
  username: 'alice',
  age: 30,
  gender: null,
  gamesPlayed: 4,
  victories: 1,
  averageScore: 2.5,
};

describe('ProfilePage', () => {
  it('shows the stats without click ratios and saves a new age', async () => {
    const api = {
      getMyProfile: vi.fn(() => of(PROFILE)),
      updateMyProfile: vi.fn(() => of({ ...PROFILE, age: 31 })),
    };
    const add = vi.spyOn(MessageService.prototype, 'add');
    await render(ProfilePage, {
      providers: [
        provideRouter([]),
        MessageService,
        ProfileStore,
        { provide: LOCALE_ID, useValue: 'fr' },
        { provide: PlayersService, useValue: api },
      ],
    });
    const user = userEvent.setup();

    expect(await screen.findByText('Parties jouées')).toBeInTheDocument();
    expect(screen.getByText('Victoires').nextElementSibling).toHaveTextContent('1');
    expect(screen.getByText('Score moyen').nextElementSibling).toHaveTextContent('2,5');
    expect(screen.queryByText(/ratio/i)).toBeNull();
    expect(screen.queryByRole('button', { name: /mot de passe/i })).toBeNull();
    expect(screen.getByLabelText("Nom d'utilisateur")).toHaveAttribute('readonly');

    const age = screen.getByLabelText('Âge');
    await user.clear(age);
    await user.type(age, '31');
    await user.click(screen.getByRole('button', { name: 'Mettre à jour' }));

    expect(api.updateMyProfile).toHaveBeenCalledWith(
      { updateProfileRequest: { age: 31, gender: null } },
      'body',
      false,
      expect.anything(),
    );
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ summary: 'Modification réussie !' }),
    );
    add.mockRestore();
  });
});
