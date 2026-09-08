import { Component, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { fromLazyEvent } from '../../core/api/paged-list';
import { AuthService } from '../../core/auth/auth.service';
import { MessagesApi } from './messages.api';
import { MessageStatus, OutboundMessage } from './messages.model';

type StatusFilter = 'all' | 'in_progress' | 'delivered' | 'failed';

@Component({
  selector: 'app-messages',
  imports: [DatePipe, TableModule],
  templateUrl: './messages.html',
  styleUrl: '../../shared/list-page.css',
})
export class Messages implements OnDestroy {
  private readonly api = inject(MessagesApi);
  readonly auth = inject(AuthService);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly messages = signal<OutboundMessage[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly detailOpen = signal(false);
  readonly selected = signal<OutboundMessage | null>(null);
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly deliveredCount = signal(0);
  readonly failedCount = signal(0);
  readonly inFlightCount = signal(0);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'in_progress', label: 'In progress' },
    { id: 'delivered', label: 'Delivered' },
    { id: 'failed', label: 'Failed' },
  ];

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

  async openDetail(message: OutboundMessage): Promise<void> {
    this.selected.set(message);
    this.detailOpen.set(true);
    try {
      this.selected.set(await this.api.get(message.id));
    } catch {
      this.selected.set(message);
    }
  }

  closeDetail(): void {
    this.detailOpen.set(false);
    this.selected.set(null);
  }

  preview(text: string | null | undefined): string {
    const value = (text ?? '').replace(/\s+/g, ' ').trim();
    if (value.length <= 72) {
      return value || '—';
    }
    return `${value.slice(0, 72)}…`;
  }

  statusLabel(status?: MessageStatus | string | null): string {
    if (!status) {
      return '—';
    }
    return status.replaceAll('_', ' ').toLowerCase();
  }

  statusClass(status?: MessageStatus | string | null): string {
    if (status === 'DELIVERED') {
      return 'live';
    }
    if (status === 'FAILED' || status === 'REJECTED' || status === 'EXPIRED') {
      return 'bad';
    }
    return 'warn';
  }

  formatCost(value: number | string | null | undefined): string {
    if (value == null || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(amount)) {
      return String(value);
    }
    return amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 });
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
      this.messages.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.deliveredCount.set(page.deliveredCount ?? 0);
      this.failedCount.set(page.failedCount ?? 0);
      this.inFlightCount.set(page.inFlightCount ?? 0);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load messages.');
    } finally {
      this.loading.set(false);
    }
  }
}
