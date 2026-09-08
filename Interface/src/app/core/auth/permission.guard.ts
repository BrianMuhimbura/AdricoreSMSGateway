import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (route.data['partnerOnly'] && !auth.isPartner()) {
    return router.createUrlTree(['/dashboard']);
  }
  const required = route.data['permissions'] as string[] | undefined;
  if (!required?.length) {
    return true;
  }
  if (auth.hasAny(...required)) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
