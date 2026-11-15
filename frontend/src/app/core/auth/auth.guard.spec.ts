import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthStore } from './auth-store';
import { authGuard } from './auth.guard';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Blank {}

describe('authGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
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
  });

  it('redirects an unauthenticated player to the login page with a return URL', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/games/42');

    expect(TestBed.inject(Router).url).toBe('/auth/login?returnUrl=%2Fgames%2F42');
  });

  it('lets an authenticated player through', async () => {
    TestBed.inject(AuthStore).setCurrentUser({ username: 'alice' });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/games/42');

    expect(TestBed.inject(Router).url).toBe('/games/42');
  });
});
