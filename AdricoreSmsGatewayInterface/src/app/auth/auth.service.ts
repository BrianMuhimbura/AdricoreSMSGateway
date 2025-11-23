import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _authenticated = signal<boolean>(false);
  private readonly _username = signal<string | null>(null);

  constructor(private router: Router) {}

  isAuthenticated() {
    return this._authenticated();
  }

  username() {
    return this._username();
  }

  login(username: string, password: string): boolean {
    if (!username || !password) {
      return false;
    }
    this._authenticated.set(true);
    this._username.set(username);
    return true;
  }

  logout() {
    this._authenticated.set(false);
    this._username.set(null);
    this.router.navigate(['/login']);
  }
}

