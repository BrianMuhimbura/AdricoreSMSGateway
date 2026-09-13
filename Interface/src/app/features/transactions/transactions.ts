import { Component, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { LedgerAccount, fromLazyEvent } from '../../core/api/paged-list';
import { AuthService } from '../../core/auth/auth.service';
import { TransactionsApi } from './transactions.api';
import { CreditTx, LedgerAccountRef } from './transactions.model';

type StatusFilter = 'all' | 'TOPUP' | 'CONSUMPTION' | 'INCOME' | 'REFUND' | 'REVERSAL' | 'ALLOCATE';

@Component({
  selector: 'app-transactions',
  imports: [DatePipe, TableModule],
  templateUrl: './transactions.html',
  styleUrl: '../../shared/list-page.css',
})
export class Transactions implements OnDestroy {
  private readonly api = inject(TransactionsApi);
  readonly auth = inject(AuthService);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly transactions = signal<CreditTx[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly topupCount = signal(0);
  readonly consumptionCount = signal(0);
  readonly incomeCount = signal(0);
  readonly control = signal<LedgerAccount | null>(null);
  readonly operations = signal<LedgerAccount | null>(null);
  readonly income = signal<LedgerAccount | null>(null);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'TOPUP', label: 'Top up' },
    { id: 'CONSUMPTION', label: 'Consumption' },
    { id: 'INCOME', label: 'Income' },
    { id: 'REFUND', label: 'Refund' },
    { id: 'REVERSAL', label: 'Reversal' },
  ];

  get visibleFilters() {
    return this.auth.isPartner() ? this.filters.filter((filter) => filter.id !== 'INCOME') : this.filters;
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
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

  typeLabel(type?: string | null): string {
    if (!type) {
      return '—';
    }
    return type.replaceAll('_', ' ').toLowerCase();
  }

  typeClass(type?: string | null): string {
    if (type === 'TOPUP' || type === 'REFUND') {
      return 'live';
    }
    if (type === 'INCOME') {
      return 'plan';
    }
    if (type === 'CONSUMPTION') {
      return 'bad';
    }
    return '';
  }

  accountLabel(account?: LedgerAccountRef | null): string {
    if (!account) {
      return '—';
    }
    return account.name || account.code || account.partnerCode || '—';
  }

  accountCode(account?: LedgerAccountRef | null): string {
    return account?.code || account?.partnerCode || '';
  }

  formatMoney(value: number | string | null | undefined): string {
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
      this.transactions.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.topupCount.set(page.topupCount ?? 0);
      this.consumptionCount.set(page.consumptionCount ?? 0);
      this.incomeCount.set(page.incomeCount ?? 0);
      this.control.set(page.ledger?.control ?? null);
      this.operations.set(page.ledger?.operations ?? null);
      this.income.set(page.ledger?.income ?? null);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load transactions.');
    } finally {
      this.loading.set(false);
    }
  }
}
