import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), AuthService],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('stores a JWT session after a successful login', async () => {
    const future = Math.floor(Date.now() / 1000) + 3600;
    const encode = (value: object) =>
      btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const token = [encode({ alg: 'HS256', typ: 'JWT' }), encode({ sub: 'admin', exp: future }), 'sig'].join('.');

    const login = service.login('admin', 'secret', true);
    const request = http.expectOne(`${environment.apiBaseUrl}/Auth/login/`);
    expect(request.request.body).toEqual({ username: 'admin', password: 'secret' });
    request.flush({
      returnCode: 0,
      returnMessage: 'Welcome back admin',
      returnObject: {
        token,
        user: { username: 'admin', firstName: 'Ada', lastName: 'Admin' },
      },
    });

    await login;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.displayName()).toBe('Ada Admin');
    expect(localStorage.getItem('adricore.jwt')).toBe(token);
  });

  it('rejects a failed login without creating a session', async () => {
    const login = service.login('admin', 'wrong', false);
    const request = http.expectOne(`${environment.apiBaseUrl}/Auth/login/`);
    request.flush({
      returnCode: 500,
      returnMessage: 'Bad credentials',
    });

    await expect(login).rejects.toThrow('Invalid username or password.');
    expect(service.isAuthenticated()).toBe(false);
  });
});
