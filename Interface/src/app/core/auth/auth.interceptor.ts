import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const publicAuth = /\/Auth\/(login|signup)\/?$/.test(req.url);
  const request = token && !publicAuth
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    tap({
      error: (error: HttpErrorResponse) => {
        if (error.status === 401 && auth.isAuthenticated()) {
          auth.logout();
        }
      },
    }),
  );
};
