import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/auth.models';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);
  readonly pageTitle = signal('Dashboard');

  readonly links = computed(() => {
    const items = [
      { path: '/dashboard', label: 'Dashboard', icon: 'grid' },
      { path: '/profile', label: 'Profile', icon: 'profile', partnerOnly: true },
      { path: '/partners', label: 'Partners', icon: 'users', permission: Permissions.MANAGE_PARTNERS },
      { path: '/users', label: 'Users', icon: 'user', permission: Permissions.MANAGE_USERS },
      { path: '/messages', label: 'Messages', icon: 'message', permission: Permissions.VIEW_MESSAGES },
      { path: '/audit-logs', label: 'Audit log', icon: 'shield', permission: Permissions.VIEW_AUDIT_LOGS },
      { path: '/transactions', label: 'Transactions', icon: 'swap', permission: Permissions.VIEW_TRANSACTIONS },
    ];
    return items.filter((link) => {
      if (link.partnerOnly && !this.auth.isPartner()) {
        return false;
      }
      if (link.permission && !this.auth.has(link.permission)) {
        return false;
      }
      return true;
    });
  });

  constructor() {
    this.syncTitle(this.router.url);
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        this.menuOpen.set(false);
        this.syncTitle(event.urlAfterRedirects);
      });
  }

  private syncTitle(url: string): void {
    const match = this.links().find((link) => url.startsWith(link.path));
    this.pageTitle.set(match?.label ?? 'Dashboard');
  }

  logout(): void {
    this.auth.logout();
  }
}
