import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/auth.models';
import { AuditLogsApi } from '../audit-logs/audit-logs.api';
import { AuditLogEntry } from '../audit-logs/audit-logs.model';
import { PartnersApi } from '../partners/partners.api';
import { Partner } from '../partners/partners.model';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  readonly auth = inject(AuthService);
  private readonly auditLogs = inject(AuditLogsApi);
  private readonly partnersApi = inject(PartnersApi);

  readonly canViewAudit = signal(false);
  readonly isPartnerUser = signal(false);
  readonly auditLoading = signal(false);
  readonly auditError = signal<string | null>(null);
  readonly recentLogs = signal<AuditLogEntry[]>([]);
  readonly auditTotal = signal(0);
  readonly auditToday = signal(0);
  readonly auditFailed = signal(0);
  readonly partner = signal<Partner | null>(null);

  readonly cards: {
    label: string;
    value: string;
    hint: string;
    link?: string;
    linkLabel?: string;
    permission?: string;
  }[] = [
    {
      label: 'Outbound messages',
      value: 'Open',
      hint: 'Inspect sent SMS, delivery status, and provider ids.',
      link: '/messages',
      linkLabel: 'View messages',
      permission: Permissions.VIEW_MESSAGES,
    },
    {
      label: 'Delivery success',
      value: '—',
      hint: 'Live receipt rates land here.',
      permission: Permissions.VIEW_MESSAGES,
    },
    {
      label: 'Active partners',
      value: 'Open',
      hint: 'Create, edit, and deactivate operators.',
      link: '/partners',
      linkLabel: 'Manage partners',
      permission: Permissions.MANAGE_PARTNERS,
    },
    {
      label: 'Transactions',
      value: 'Open',
      hint: 'Inspect credit top-ups and SMS consumption.',
      link: '/transactions',
      linkLabel: 'View transactions',
      permission: Permissions.VIEW_TRANSACTIONS,
    },
    { label: 'Gateway health', value: 'Ready', hint: 'You are authenticated and online.' },
  ];

  ngOnInit(): void {
    const partnerUser = this.auth.isPartner();
    this.isPartnerUser.set(partnerUser);
    this.canViewAudit.set(this.auth.has(Permissions.VIEW_AUDIT_LOGS));
    if (this.canViewAudit()) {
      void this.loadAudit();
    }
    if (partnerUser) {
      void this.loadPartner();
    }
  }

  actionLabel(action?: string | null): string {
    if (!action) {
      return '—';
    }
    const parts = action.split('.');
    const verb = parts.length > 1 ? parts.slice(1).join('.') : action;
    return verb.replaceAll(/([A-Z])/g, ' $1').replaceAll('_', ' ').trim();
  }

  formatMoney(value: number | string | null | undefined, currency?: string | null): string {
    if (value == null || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(amount)) {
      return String(value);
    }
    const code = currency || this.partner()?.currency || 'UGX';
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: code,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(amount);
    } catch {
      return `${code} ${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
  }

  visibleCards() {
    return this.cards.filter((card) => !card.permission || this.auth.has(card.permission));
  }

  private async loadPartner(): Promise<void> {
    try {
      this.partner.set(await this.partnersApi.profile());
    } catch {
      this.partner.set(null);
    }
  }

  private async loadAudit(): Promise<void> {
    this.auditLoading.set(true);
    this.auditError.set(null);
    try {
      const page = await this.auditLogs.list({
        first: 0,
        rows: 8,
        sortField: 'createdAt',
        sortOrder: -1,
      });
      this.recentLogs.set(page.items ?? []);
      this.auditTotal.set(page.totalCount ?? page.totalRecords ?? 0);
      this.auditToday.set(page.todayCount ?? 0);
      this.auditFailed.set(page.failedCount ?? 0);
    } catch (error) {
      this.auditError.set(error instanceof Error ? error.message : 'Unable to load audit activity.');
    } finally {
      this.auditLoading.set(false);
    }
  }
}
