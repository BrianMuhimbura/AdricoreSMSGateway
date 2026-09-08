import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return true;
  }
  if (auth.mustChangePassword()) {
    return router.createUrlTree(['/change-password']);
  }
  return router.createUrlTree(['/dashboard']);
};
