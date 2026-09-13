import { Component, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { fromLazyEvent } from '../../core/api/paged-list';
import { AuthService } from '../../core/auth/auth.service';
import { MessagesApi } from './messages.api';
import { MessageStatus, OutboundMessage } from './messages.model';

type StatusFilter = 'all' | 'in_progress' | 'delivered' | 'failed';
type Composer = 'test' | 'bulk' | null;

@Component({
  selector: 'app-messages',
  imports: [DatePipe, TableModule, ReactiveFormsModule],
  templateUrl: './messages.html',
  styleUrl: '../../shared/list-page.css',
})
export class Messages implements OnDestroy {
  private readonly api = inject(MessagesApi);
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly messages = signal<OutboundMessage[]>([]);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly detailOpen = signal(false);
  readonly composer = signal<Composer>(null);
  readonly selected = signal<OutboundMessage | null>(null);
  readonly bulkPhones = signal<string[]>([]);
  readonly bulkFileName = signal<string | null>(null);
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

  readonly testForm = this.fb.nonNullable.group({
    to: ['', [Validators.required, Validators.minLength(9)]],
    text: ['', [Validators.required, Validators.minLength(1)]],
  });

  readonly bulkForm = this.fb.nonNullable.group({
    text: ['', [Validators.required, Validators.minLength(1)]],
  });

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    if (this.noticeTimer) {
      clearTimeout(this.noticeTimer);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.sending()) {
      return;
    }
    if (this.composer()) {
      this.closeComposer();
      return;
    }
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

  openTest(): void {
    this.error.set(null);
    this.testForm.reset({ to: '', text: '' });
    this.composer.set('test');
  }

  openBulk(): void {
    this.error.set(null);
    this.bulkForm.reset({ text: '' });
    this.bulkPhones.set([]);
    this.bulkFileName.set(null);
    this.composer.set('bulk');
  }

  closeComposer(): void {
    if (this.sending()) {
      return;
    }
    this.composer.set(null);
    this.error.set(null);
    this.bulkPhones.set([]);
    this.bulkFileName.set(null);
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

  onBulkFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.bulkFileName.set(file.name);
    const reader = new FileReader();
    reader.onload = () => {
      const phones = this.parseRecipients(String(reader.result || ''));
      this.bulkPhones.set(phones);
      if (!phones.length) {
        this.error.set('No phone numbers were found in that file.');
      } else {
        this.error.set(null);
      }
    };
    reader.onerror = () => {
      this.error.set('Unable to read the selected file.');
    };
    reader.readAsText(file);
  }

  async submitTest(): Promise<void> {
    if (this.testForm.invalid) {
      this.testForm.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    try {
      const value = this.testForm.getRawValue();
      const result = await this.api.testSend({
        to: value.to.trim(),
        text: value.text.trim(),
      });
      this.sending.set(false);
      this.closeComposer();
      this.flash(result.success === false ? result.error || 'SMS was not accepted.' : 'Test SMS sent.');
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to send the test SMS.');
    } finally {
      this.sending.set(false);
    }
  }

  async submitBulk(): Promise<void> {
    if (this.bulkForm.invalid) {
      this.bulkForm.markAllAsTouched();
      return;
    }
    if (!this.bulkPhones().length) {
      this.error.set('Upload a file with at least one phone number.');
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    try {
      const result = await this.api.bulkSend({
        to: this.bulkPhones(),
        text: this.bulkForm.controls.text.value.trim(),
      });
      const accepted = result.accepted ?? 0;
      const requested = result.requested ?? this.bulkPhones().length;
      this.sending.set(false);
      this.closeComposer();
      this.flash(
        accepted === requested
          ? `Sent ${accepted} SMS.`
          : `Sent ${accepted} of ${requested} SMS.`,
      );
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to send the bulk SMS.');
    } finally {
      this.sending.set(false);
    }
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
    if (status === 'SENT') {
      return 'success';
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

  private parseRecipients(raw: string): string[] {
    const lines = raw.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    if (!lines.length) {
      return [];
    }
    const header = this.firstColumn(lines[0]).toLowerCase();
    const start = ['phone', 'to', 'msisdn', 'number', 'mobile', 'recipient'].includes(header) ? 1 : 0;
    const phones: string[] = [];
    for (let index = start; index < lines.length; index += 1) {
      const value = this.firstColumn(lines[index]);
      if (value && !phones.includes(value)) {
        phones.push(value);
      }
    }
    return phones.slice(0, 100);
  }

  private firstColumn(line: string): string {
    return line.split(/[,;\t]/)[0]?.replace(/^["']|["']$/g, '').trim() ?? '';
  }

  private flash(message: string): void {
    this.notice.set(message);
    if (this.noticeTimer) {
      clearTimeout(this.noticeTimer);
    }
    this.noticeTimer = setTimeout(() => this.notice.set(null), 4000);
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
    if (!this.composer()) {
      this.error.set(null);
    }
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
