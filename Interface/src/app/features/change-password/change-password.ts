import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.html',
  styleUrl: '../login/login.css',
})
export class ChangePassword implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private errorTimer: ReturnType<typeof setTimeout> | null = null;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly year = new Date().getFullYear();

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  });

  togglePassword(): void {
    this.showPassword.update((value) => !value);
  }

  ngOnDestroy(): void {
    this.clearError();
  }

  logout(): void {
    this.auth.logout();
  }

  async submit(): Promise<void> {
    this.clearError();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword, confirmPassword } = this.form.getRawValue();
    if (newPassword !== confirmPassword) {
      this.form.controls.confirmPassword.setErrors({ mismatch: true });
      return;
    }
    if (newPassword === currentPassword) {
      this.flashError('Choose a password that is different from the temporary one.');
      return;
    }

    this.loading.set(true);
    try {
      await this.auth.changePassword(currentPassword, newPassword);
      await this.router.navigateByUrl('/dashboard');
    } catch (error) {
      this.flashError(error instanceof Error ? error.message : 'Unable to update password.');
    } finally {
      this.loading.set(false);
    }
  }

  private flashError(message: string): void {
    this.error.set(message);
    if (this.errorTimer) {
      clearTimeout(this.errorTimer);
    }
    this.errorTimer = setTimeout(() => this.error.set(null), 5000);
  }

  private clearError(): void {
    if (this.errorTimer) {
      clearTimeout(this.errorTimer);
      this.errorTimer = null;
    }
    this.error.set(null);
  }
}
