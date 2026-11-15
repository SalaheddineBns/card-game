import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PlayerDto } from '../api';
import { ALICE, provideAuthApi } from '../../../testing/auth';
import { authGuard } from './auth.guard';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Blank {}

function setup(sessionPlayer: PlayerDto | null): void {
  TestBed.configureTestingModule({
    providers: [
      provideAuthApi(sessionPlayer),
      provideRouter([
        { path: 'auth/login', component: Blank },
        {
          path: '',
          canMatch: [authGuard],
          children: [{ path: 'games/:id', component: Blank }],
        },
      ]),
    ],
  });
}

describe('authGuard', () => {
  it('redirects an unauthenticated player to the login page with a return URL', async () => {
    setup(null);
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/games/42');

    expect(TestBed.inject(Router).url).toBe('/auth/login?returnUrl=%2Fgames%2F42');
  });

  it('lets a player with a server session through', async () => {
    setup(ALICE);
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/games/42');

    expect(TestBed.inject(Router).url).toBe('/games/42');
  });
});
