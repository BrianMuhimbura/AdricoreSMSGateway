import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { passwordChangeGuard } from './core/auth/password-change.guard';
import { permissionGuard } from './core/auth/permission.guard';
import { Permissions } from './core/auth/auth.models';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'change-password',
    canActivate: [passwordChangeGuard],
    loadComponent: () =>
      import('./features/change-password/change-password').then((m) => m.ChangePassword),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'partners',
        canActivate: [permissionGuard],
        data: { permissions: [Permissions.MANAGE_PARTNERS] },
        loadComponent: () => import('./features/partners/partners').then((m) => m.Partners),
      },
      {
        path: 'profile',
        canActivate: [permissionGuard],
        data: { partnerOnly: true },
        loadComponent: () =>
          import('./features/partner-profile/partner-profile').then((m) => m.PartnerProfile),
      },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permissions: [Permissions.MANAGE_USERS] },
        loadComponent: () => import('./features/users/users').then((m) => m.Users),
      },
      {
        path: 'messages',
        canActivate: [permissionGuard],
        data: { permissions: [Permissions.VIEW_MESSAGES] },
        loadComponent: () => import('./features/messages/messages').then((m) => m.Messages),
      },
      {
        path: 'audit-logs',
        canActivate: [permissionGuard],
        data: { permissions: [Permissions.VIEW_AUDIT_LOGS] },
        loadComponent: () => import('./features/audit-logs/audit-logs').then((m) => m.AuditLogs),
      },
      {
        path: 'transactions',
        canActivate: [permissionGuard],
        data: { permissions: [Permissions.VIEW_TRANSACTIONS] },
        loadComponent: () =>
          import('./features/transactions/transactions').then((m) => m.Transactions),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
