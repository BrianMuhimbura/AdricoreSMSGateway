import { Component, HostListener, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { AuthService } from '../../core/auth/auth.service';
import { fromLazyEvent } from '../../core/api/paged-list';
import { AuditLogsApi } from './audit-logs.api';
import { AuditLogEntry } from './audit-logs.model';

type StatusFilter = 'all' | 'failed' | 'Partners' | 'Users' | 'Auth' | 'SMS' | 'Transactions';

@Component({
  selector: 'app-audit-logs',
  imports: [DatePipe, TableModule],
  templateUrl: './audit-logs.html',
  styleUrl: '../../shared/list-page.css',
})
export class AuditLogs implements OnDestroy {
  private readonly api = inject(AuditLogsApi);
  readonly auth = inject(AuthService);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly logs = signal<AuditLogEntry[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly detailOpen = signal(false);
  readonly selected = signal<AuditLogEntry | null>(null);
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly todayCount = signal(0);
  readonly failedCount = signal(0);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'failed', label: 'Failed' },
    { id: 'Partners', label: 'Partners' },
    { id: 'Users', label: 'Users' },
    { id: 'Auth', label: 'Auth' },
    { id: 'SMS', label: 'SMS' },
    { id: 'Transactions', label: 'Transactions' },
  ];
  readonly visibleFilters = computed(() =>
    this.auth.isPartner() ? this.filters.filter((filter) => filter.id !== 'Partners') : this.filters,
  );

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.detailOpen()) {
      this.closeDetail();
    }
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    void this.load(event);
  }

  onQueryInput(value: string): void {
    this.query.set(value);
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.applyFilters(), 300);
  }

  setStatus(status: StatusFilter): void {
    if (this.statusFilter() === status) {
      return;
    }
    this.statusFilter.set(status);
    this.applyFilters();
  }

  refresh(): void {
    void this.load(this.lastEvent);
  }

  async openDetail(log: AuditLogEntry): Promise<void> {
    this.selected.set(log);
    this.detailOpen.set(true);
    try {
      this.selected.set(await this.api.get(log.id));
    } catch {
      this.selected.set(log);
    }
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selected.set(null);
  }

  actionLabel(action?: string | null): string {
    if (!action) {
      return '—';
    }
    const parts = action.split('.');
    const verb = parts.length > 1 ? parts.slice(1).join('.') : action;
    return verb.replaceAll(/([A-Z])/g, ' $1').replaceAll('_', ' ').trim();
  }

  formatDetails(details: Record<string, unknown> | null | undefined): string {
    if (!details) {
      return '—';
    }
    return JSON.stringify(details, null, 2);
  }

  private applyFilters(): void {
    if (this.table && this.table.first !== 0) {
      this.table.first = 0;
      return;
    }
    void this.load({ ...this.lastEvent, first: 0 });
  }

  private async load(event: TableLazyLoadEvent): Promise<void> {
    this.lastEvent = event;
    this.loading.set(true);
    this.error.set(null);
    try {
      const page = await this.api.list({
        ...fromLazyEvent(event, { rows: this.pageSize, sortField: 'createdAt', sortOrder: -1 }),
        query: this.query().trim(),
        status: this.statusFilter() === 'all' ? undefined : this.statusFilter(),
      });
      this.logs.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.todayCount.set(page.todayCount ?? 0);
      this.failedCount.set(page.failedCount ?? 0);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load audit logs.');
    } finally {
      this.loading.set(false);
    }
  }
}
