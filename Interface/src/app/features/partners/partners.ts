import { Component, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { PartnersApi, PartnerLookup } from './partners.api';
import { Partner, PartnerPayload, PartnerStatus } from './partners.model';
import { fromLazyEvent } from '../../core/api/paged-list';

type StatusFilter = 'all' | PartnerStatus;

@Component({
  selector: 'app-partners',
  imports: [ReactiveFormsModule, DatePipe, TableModule],
  templateUrl: './partners.html',
  styleUrl: '../../shared/list-page.css',
})
export class Partners implements OnDestroy {
  private readonly api = inject(PartnersApi);
  private readonly fb = inject(FormBuilder);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly partners = signal<Partner[]>([]);
  readonly parentChoices = signal<PartnerLookup[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly editorOpen = signal(false);
  readonly confirmOpen = signal(false);
  readonly editing = signal<Partner | null>(null);
  readonly pending = signal<Partner | null>(null);
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly activeCount = signal(0);
  readonly inactiveCount = signal(0);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly currencies = ['UGX', 'KES', 'TZS', 'RWF', 'USD'];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'active', label: 'Active' },
    { id: 'inactive', label: 'Inactive' },
  ];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.email]],
    phone: [''],
    currency: ['UGX', Validators.required],
    parentPartnerId: [''],
    status: ['active'],
  });

  ngOnDestroy(): void {
    if (this.noticeTimer) {
      clearTimeout(this.noticeTimer);
    }
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.saving()) {
      return;
    }
    if (this.confirmOpen()) {
      this.closeConfirm();
      return;
    }
    if (this.editorOpen()) {
      this.closeEditor();
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

  async openCreate(): Promise<void> {
    this.editing.set(null);
    this.error.set(null);
    this.form.reset({
      name: '',
      email: '',
      phone: '',
      currency: 'UGX',
      parentPartnerId: '',
      status: 'active',
    });
    this.editorOpen.set(true);
    await this.loadParents();
  }

  async openEdit(partner: Partner): Promise<void> {
    this.editing.set(partner);
    this.error.set(null);
    this.form.reset({
      name: partner.name,
      email: partner.email ?? '',
      phone: partner.phone ?? '',
      currency: partner.currency || 'UGX',
      parentPartnerId: partner.parentPartnerId ?? '',
      status: partner.status,
    });
    this.editorOpen.set(true);
    await this.loadParents();
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
  }

  askDeactivate(partner: Partner): void {
    this.pending.set(partner);
    this.confirmOpen.set(true);
  }

  closeConfirm(): void {
    this.confirmOpen.set(false);
    this.pending.set(null);
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const payload: PartnerPayload = {
      name: value.name.trim(),
      email: value.email.trim() || null,
      phone: value.phone.trim() || null,
      currency: value.currency,
      parentPartnerId: value.parentPartnerId || null,
    };

    this.saving.set(true);
    this.error.set(null);
    try {
      const current = this.editing();
      if (current) {
        const updated = await this.api.update(current.id, {
          ...payload,
          status: value.status as PartnerStatus,
        });
        this.flash(`Updated ${updated.name}.`);
      } else {
        const created = await this.api.create(payload);
        this.flash(`Created ${created.name} (${created.partnerCode}).`);
      }
      this.closeEditor();
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to save partner.');
    } finally {
      this.saving.set(false);
    }
  }

  async confirmDeactivate(): Promise<void> {
    const partner = this.pending();
    if (!partner) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.api.deactivate(partner.id);
      this.flash(`${partner.name} is now inactive.`);
      this.closeConfirm();
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to deactivate partner.');
    } finally {
      this.saving.set(false);
    }
  }

  async reactivate(partner: Partner): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.api.activate(partner.id);
      this.flash(`${partner.name} is active again.`);
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to reactivate partner.');
    } finally {
      this.saving.set(false);
    }
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
      this.partners.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.activeCount.set(page.activeCount ?? 0);
      this.inactiveCount.set(page.inactiveCount ?? 0);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load partners.');
    } finally {
      this.loading.set(false);
    }
  }

  private async loadParents(): Promise<void> {
    try {
      const currentId = this.editing()?.id;
      const rows = await this.api.lookup();
      this.parentChoices.set((rows ?? []).filter((partner) => partner.id !== currentId));
    } catch {
      this.parentChoices.set([]);
    }
  }

  private flash(message: string): void {
    this.notice.set(message);
    if (this.noticeTimer) {
      clearTimeout(this.noticeTimer);
    }
    this.noticeTimer = setTimeout(() => this.notice.set(null), 4000);
  }
}
