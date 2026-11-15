import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { ALICE, provideAuthApi, signIn } from '../../../testing/auth';
import { AuthStore } from '../auth/auth-store';
import { HttpContext } from '@angular/common/http';
import { errorInterceptor } from './error.interceptor';
import { SKIP_ERROR_TOAST } from './skip-error-toast';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let messages: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        provideAuthApi(ALICE),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
    vi.spyOn(messages, 'add');
  });

  afterEach(() => backend.verify());

  it('clears the user and redirects to login on 401', async () => {
    const auth = TestBed.inject(AuthStore);
    await signIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const request = firstValueFrom(http.get('/api/players/me'));
    backend.expectOne('/api/players/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(request).rejects.toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/auth/login'], expect.anything());
  });

  it('turns a ProblemDetail into an error toast', async () => {
    const request = firstValueFrom(http.post('/api/invitations', {}));
    backend
      .expectOne('/api/invitations')
      .flush(
        { title: 'Joueur indisponible', detail: 'bob est déjà en partie', status: 409 },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(request).rejects.toBeTruthy();
    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'Joueur indisponible',
        detail: 'bob est déjà en partie',
      }),
    );
  });

  it('stays quiet when the caller handles the error itself', async () => {
    const request = firstValueFrom(
      http.post(
        '/api/auth/register',
        {},
        { context: new HttpContext().set(SKIP_ERROR_TOAST, true) },
      ),
    );
    backend
      .expectOne('/api/auth/register')
      .flush({ title: 'Pseudo déjà pris' }, { status: 409, statusText: 'Conflict' });

    await expect(request).rejects.toBeTruthy();
    expect(messages.add).not.toHaveBeenCalled();
  });
});
