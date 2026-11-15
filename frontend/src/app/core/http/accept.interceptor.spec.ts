import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { acceptInterceptor } from './accept.interceptor';

describe('acceptInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([acceptInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });

  it('also accepts JSON when a request only accepts problem details', () => {
    TestBed.inject(HttpClient)
      .post('/api/auth/logout', null, { headers: { Accept: 'application/problem+json' } })
      .subscribe();

    const req = TestBed.inject(HttpTestingController).expectOne('/api/auth/logout');
    expect(req.request.headers.get('Accept')).toBe('application/json, application/problem+json');
  });

  it('leaves other Accept headers alone', () => {
    TestBed.inject(HttpClient)
      .get('/api/players/me', { headers: { Accept: 'application/json' } })
      .subscribe();

    const req = TestBed.inject(HttpTestingController).expectOne('/api/players/me');
    expect(req.request.headers.get('Accept')).toBe('application/json');
  });
});
