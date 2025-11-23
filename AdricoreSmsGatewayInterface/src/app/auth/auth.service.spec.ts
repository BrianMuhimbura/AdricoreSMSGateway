import { AuthService } from './auth.service';
import { Router } from '@angular/router';

class RouterStub {
  navigateCalls: any[] = [];
  navigate(commands: any[]) { this.navigateCalls.push(commands); }
}

describe('AuthService', () => {
  let service: AuthService;
  let router: RouterStub;

  beforeEach(() => {
    router = new RouterStub();
    service = new AuthService(router as any as Router);
  });

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBeNull();
  });

  it('rejects empty credentials', () => {
    expect(service.login('', '')).toBe(false);
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logs in with non-empty credentials', () => {
    const ok = service.login('user', 'pass');
    expect(ok).toBe(true);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('user');
  });

  it('logs out and navigates to login', () => {
    service.login('user', 'pass');
    service.logout();
    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigateCalls.pop()).toEqual(['/login']);
  });
});

