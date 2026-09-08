import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, LoginPayload, OperationReturn, SignupRequest, SignupResult } from './auth.models';
import { decodeJwt, isJwtExpired } from './jwt.util';

const TOKEN_KEY = 'adricore.jwt';
const USER_KEY = 'adricore.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);

  private readonly tokenState = signal<string | null>(null);
  private readonly userState = signal<AuthUser | null>(null);

  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => {
    const token = this.tokenState();
    return !!token && !isJwtExpired(token);
  });
  readonly mustChangePassword = computed(() => !!this.userState()?.mustChangePassword);
  readonly isPartner = computed(() => !!this.userState()?.partnerId);
  readonly permissions = computed(() => this.userState()?.permissions ?? []);
  readonly displayName = computed(() => {
    const user = this.userState();
    if (!user) {
      return 'Administrator';
    }
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return fullName || user.username;
  });

  has(permission: string): boolean {
    const perms = this.permissions();
    return perms.includes(permission);
  }

  hasAny(...permissions: string[]): boolean {
    return permissions.some((permission) => this.has(permission));
  }

  constructor() {
    this.restoreSession();
  }

  async login(username: string, password: string, remember: boolean): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.post<OperationReturn>(`${environment.apiBaseUrl}/Auth/login/`, {
          username,
          password,
        }),
      );

      if (response.returnCode !== 0) {
        throw new Error(this.friendlyMessage(response.returnMessage));
      }

      const payload = this.extractPayload(response, username);
      this.persistSession(payload.token, payload.user, remember);
    } catch (error) {
      throw new Error(this.readError(error));
    }
  }

  async signup(payload: SignupRequest): Promise<SignupResult> {
    try {
      const response = await firstValueFrom(
        this.http.post<OperationReturn<SignupResult>>(`${environment.apiBaseUrl}/Auth/signup/`, payload),
      );
      if (response.returnCode !== 0) {
        throw new Error(response.returnMessage || 'Unable to create account.');
      }
      if (!response.returnObject) {
        throw new Error('Account was created, but no details were returned.');
      }
      return response.returnObject;
    } catch (error) {
      throw new Error(this.readSignupError(error));
    }
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.post<OperationReturn<AuthUser>>(`${environment.apiBaseUrl}/Auth/changePassword/`, {
          currentPassword,
          newPassword,
        }),
      );
      if (response.returnCode !== 0) {
        throw new Error(response.returnMessage || 'Unable to update password.');
      }
      const current = this.userState();
      const next: AuthUser = {
        username: current?.username || response.returnObject?.username || '',
        ...current,
        ...(response.returnObject ?? {}),
        mustChangePassword: false,
      };
      this.patchStoredUser(next);
    } catch (error) {
      throw new Error(this.readSignupError(error));
    }
  }

  logout(redirect = true): void {
    this.tokenState.set(null);
    this.userState.set(null);
    this.clearStorage();
    if (redirect) {
      void this.router.navigateByUrl('/login');
    }
  }

  private extractPayload(response: OperationReturn, fallbackUsername: string): LoginPayload {
    const data = response.returnObject;
    const token = data?.token;
    if (!token || isJwtExpired(token)) {
      throw new Error('Authentication token was not returned.');
    }

    const claims = decodeJwt(token);
    const rawUser = data.user;
    const user: AuthUser = {
      id: rawUser?.id,
      username: rawUser?.username || claims?.sub || fallbackUsername,
      firstName: rawUser?.firstName,
      lastName: rawUser?.lastName,
      email: rawUser?.email,
      roleCode: rawUser?.roleCode || claims?.role_code,
      isActive: rawUser?.isActive,
      partnerId: rawUser?.partnerId ?? claims?.partner_id ?? null,
      mustChangePassword: rawUser?.mustChangePassword ?? !!claims?.must_change_password,
      permissions: claims?.permissions ?? rawUser?.permissions ?? [],
    };

    return { token, user };
  }

  private persistSession(token: string, user: AuthUser | undefined, remember: boolean): void {
    this.tokenState.set(token);
    this.userState.set(user ?? null);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const store = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    other.removeItem(TOKEN_KEY);
    other.removeItem(USER_KEY);
    store.setItem(TOKEN_KEY, token);
    if (user) {
      store.setItem(USER_KEY, JSON.stringify(user));
    }
  }

  private patchStoredUser(user: AuthUser): void {
    this.userState.set(user);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const store = localStorage.getItem(TOKEN_KEY) ? localStorage : sessionStorage;
    store.setItem(USER_KEY, JSON.stringify(user));
  }

  private restoreSession(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const token = localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
    if (!token || isJwtExpired(token)) {
      this.clearStorage();
      return;
    }

    const rawUser = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY);
    const claims = decodeJwt(token);
    const stored = rawUser ? (JSON.parse(rawUser) as AuthUser) : { username: claims?.sub ?? 'user' };
    this.tokenState.set(token);
    this.userState.set({
      ...stored,
      mustChangePassword: stored.mustChangePassword ?? !!claims?.must_change_password,
      partnerId: stored.partnerId ?? claims?.partner_id ?? null,
      permissions: claims?.permissions ?? stored.permissions ?? [],
    });
  }

  private clearStorage(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
  }

  private readError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as OperationReturn | undefined;
      if (body?.returnMessage) {
        return this.friendlyMessage(body.returnMessage);
      }
      if (error.status === 0) {
        return 'Cannot reach the SMS gateway. Confirm the API is running on port 8080.';
      }
      return this.friendlyMessage(error.message);
    }
    if (error instanceof Error) {
      return this.friendlyMessage(error.message);
    }
    return 'Unable to sign in. Please try again.';
  }

  private readSignupError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as OperationReturn | undefined;
      if (body?.returnMessage) {
        return body.returnMessage.replace(/_/g, ' ');
      }
      if (error.status === 0) {
        return 'Cannot reach the SMS gateway. Confirm the API is running on port 8080.';
      }
      return error.message;
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'Unable to complete the request. Please try again.';
  }

  private friendlyMessage(message?: string): string {
    const value = (message ?? '').toLowerCase();
    if (!value) {
      return 'Unable to sign in. Please try again.';
    }
    if (value.includes('bad credentials') || value.includes('password') || value.includes('username') || value.includes('user not found')) {
      return 'Invalid username or password.';
    }
    if (value.includes('inactive') || value.includes('locked') || value.includes('disabled')) {
      return 'This account is inactive. Contact an administrator.';
    }
    return message!.replace(/_/g, ' ');
  }
}
