import { Component, HostListener, OnDestroy, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private errorTimer: ReturnType<typeof setTimeout> | null = null;
  private signupErrorTimer: ReturnType<typeof setTimeout> | null = null;

  readonly loading = signal(false);
  readonly signingUp = signal(false);
  readonly error = signal<string | null>(null);
  readonly signupError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly signupOpen = signal(false);
  readonly year = new Date().getFullYear();
  readonly currencies = ['UGX', 'KES', 'TZS', 'RWF', 'USD'];

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(4)]],
    remember: [true],
  });

  readonly signupForm = this.fb.nonNullable.group({
    partnerName: ['', [Validators.required, Validators.minLength(2)]],
    firstName: ['', [Validators.required, Validators.minLength(2)]],
    lastName: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    currency: ['UGX', Validators.required],
  });

  get username() {
    return this.form.controls.username;
  }

  get password() {
    return this.form.controls.password;
  }

  togglePassword(): void {
    this.showPassword.update((value) => !value);
  }

  ngOnDestroy(): void {
    this.clearTimer('notice');
    this.clearTimer('error');
    this.clearTimer('signupError');
  }

  openSignup(): void {
    this.clearMessage('signupError');
    this.signupForm.reset({
      partnerName: '',
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      currency: 'UGX',
    });
    this.signupOpen.set(true);
  }

  closeSignup(): void {
    if (this.signingUp()) {
      return;
    }
    this.signupOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.signupOpen()) {
      this.closeSignup();
    }
  }

  async submit(): Promise<void> {
    this.clearMessage('error');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    const { username, password, remember } = this.form.getRawValue();

    try {
      await this.auth.login(username.trim(), password, remember);
      this.clearMessage('notice');
      if (this.auth.mustChangePassword()) {
        await this.router.navigateByUrl('/change-password');
        return;
      }
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
      await this.router.navigateByUrl(returnUrl);
    } catch (error) {
      this.flashError(error instanceof Error ? error.message : 'Unable to sign in.');
    } finally {
      this.loading.set(false);
    }
  }

  async submitSignup(): Promise<void> {
    this.clearMessage('signupError');
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    const value = this.signupForm.getRawValue();
    this.signingUp.set(true);
    try {
      const created = await this.auth.signup({
        partnerName: value.partnerName.trim(),
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        email: value.email.trim(),
        phone: value.phone.trim() || null,
        currency: value.currency,
      });
      this.signupOpen.set(false);
      this.form.controls.username.setValue(created.username);
      this.flashNotice(
        `We sent ${created.partnerCode} and a temporary password to ${created.email}. Sign in, then choose a new password.`,
      );
    } catch (error) {
      this.flashSignupError(error instanceof Error ? error.message : 'Unable to create account.');
    } finally {
      this.signingUp.set(false);
    }
  }

  private flashNotice(message: string): void {
    this.clearMessage('error');
    this.notice.set(message);
    this.clearTimer('notice');
    this.noticeTimer = setTimeout(() => this.notice.set(null), 8000);
  }

  private flashError(message: string): void {
    this.error.set(message);
    this.clearTimer('error');
    this.errorTimer = setTimeout(() => this.error.set(null), 5000);
  }

  private flashSignupError(message: string): void {
    this.signupError.set(message);
    this.clearTimer('signupError');
    this.signupErrorTimer = setTimeout(() => this.signupError.set(null), 5000);
  }

  private clearMessage(kind: 'notice' | 'error' | 'signupError'): void {
    this.clearTimer(kind);
    if (kind === 'notice') {
      this.notice.set(null);
    } else if (kind === 'error') {
      this.error.set(null);
    } else {
      this.signupError.set(null);
    }
  }

  private clearTimer(kind: 'notice' | 'error' | 'signupError'): void {
    if (kind === 'notice' && this.noticeTimer) {
      clearTimeout(this.noticeTimer);
      this.noticeTimer = null;
    } else if (kind === 'error' && this.errorTimer) {
      clearTimeout(this.errorTimer);
      this.errorTimer = null;
    } else if (kind === 'signupError' && this.signupErrorTimer) {
      clearTimeout(this.signupErrorTimer);
      this.signupErrorTimer = null;
    }
  }
}
