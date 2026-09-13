import { Component, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { fromLazyEvent } from '../../core/api/paged-list';
import { PricingProfilesApi } from './pricing-profiles.api';
import { BillingType, SmsPricingProfile, SmsPricingProfilePayload } from './pricing-profiles.model';

type StatusFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-pricing-profiles',
  imports: [ReactiveFormsModule, TableModule],
  templateUrl: './pricing-profiles.html',
  styleUrl: '../../shared/list-page.css',
})
export class PricingProfiles implements OnDestroy {
  private readonly api = inject(PricingProfilesApi);
  private readonly fb = inject(FormBuilder);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'minimumSmsVolume', sortOrder: 1 };

  @ViewChild('dt') table?: Table;

  readonly profiles = signal<SmsPricingProfile[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly editorOpen = signal(false);
  readonly editing = signal<SmsPricingProfile | null>(null);
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly activeCount = signal(0);
  readonly inactiveCount = signal(0);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly currencies = ['UGX', 'KES', 'TZS', 'RWF', 'USD'];
  readonly billingTypes: { id: BillingType; label: string }[] = [
    { id: 'PER_SMS', label: 'Per SMS' },
    { id: 'PER_SEGMENT', label: 'Per segment' },
  ];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'active', label: 'Active' },
    { id: 'inactive', label: 'Inactive' },
  ];

  readonly form = this.fb.nonNullable.group({
    profileCode: ['', [Validators.required, Validators.minLength(2)]],
    profileName: ['', [Validators.required, Validators.minLength(2)]],
    description: [''],
    currencyCode: ['UGX', Validators.required],
    providerCostPerSms: [25, [Validators.required, Validators.min(0)]],
    topupFeePerSms: [5, [Validators.required, Validators.min(0)]],
    minimumSmsVolume: [0, [Validators.required, Validators.min(0)]],
    maximumSmsVolume: ['' as number | ''],
    billingType: ['PER_SMS' as BillingType, Validators.required],
    isDefault: [false],
    isActive: [true],
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

  openCreate(): void {
    this.editing.set(null);
    this.error.set(null);
    this.form.reset({
      profileCode: '',
      profileName: '',
      description: '',
      currencyCode: 'UGX',
      providerCostPerSms: 25,
      topupFeePerSms: 5,
      minimumSmsVolume: 0,
      maximumSmsVolume: '',
      billingType: 'PER_SMS',
      isDefault: false,
      isActive: true,
    });
    this.editorOpen.set(true);
  }

  openEdit(profile: SmsPricingProfile): void {
    this.editing.set(profile);
    this.error.set(null);
    this.form.reset({
      profileCode: profile.profileCode,
      profileName: profile.profileName,
      description: profile.description ?? '',
      currencyCode: profile.currencyCode || 'UGX',
      providerCostPerSms: Number(profile.providerCostPerSms ?? 0),
      topupFeePerSms: Number(profile.topupFeePerSms ?? 0),
      minimumSmsVolume: profile.minimumSmsVolume ?? 0,
      maximumSmsVolume: profile.maximumSmsVolume == null ? '' : profile.maximumSmsVolume,
      billingType: profile.billingType || 'PER_SMS',
      isDefault: profile.isDefault,
      isActive: profile.isActive,
    });
    this.editorOpen.set(true);
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
  }

  sellingPrice(): number {
    const cost = Number(this.form.controls.providerCostPerSms.value) || 0;
    const fee = Number(this.form.controls.topupFeePerSms.value) || 0;
    return Math.round((cost + fee) * 10000) / 10000;
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const payload: SmsPricingProfilePayload = {
      profileCode: value.profileCode.trim().toUpperCase(),
      profileName: value.profileName.trim(),
      description: value.description.trim() || null,
      currencyCode: value.currencyCode,
      providerCostPerSms: Number(value.providerCostPerSms),
      topupFeePerSms: Number(value.topupFeePerSms),
      minimumSmsVolume: Number(value.minimumSmsVolume),
      maximumSmsVolume: value.maximumSmsVolume === '' || value.maximumSmsVolume == null
        ? null
        : Number(value.maximumSmsVolume),
      billingType: value.billingType,
      isDefault: value.isDefault,
      isActive: value.isActive,
    };

    this.saving.set(true);
    this.error.set(null);
    try {
      const current = this.editing();
      if (current) {
        const updated = await this.api.update(current.id, payload);
        this.flash(`Updated ${updated.profileName}.`);
      } else {
        const created = await this.api.create(payload);
        this.flash(`Created ${created.profileName}.`);
      }
      this.closeEditor();
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to save pricing profile.');
    } finally {
      this.saving.set(false);
    }
  }

  formatMoney(value: number | string | null | undefined, currency?: string | null): string {
    if (value == null || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(amount)) {
      return String(value);
    }
    const code = currency || 'UGX';
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: code,
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
      }).format(amount);
    } catch {
      return `${code} ${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`;
    }
  }

  volumeLabel(profile: SmsPricingProfile): string {
    const min = profile.minimumSmsVolume ?? 0;
    if (profile.maximumSmsVolume == null) {
      return `${min.toLocaleString()}+ SMS`;
    }
    return `${min.toLocaleString()} – ${profile.maximumSmsVolume.toLocaleString()} SMS`;
  }

  billingLabel(type?: string | null): string {
    return type === 'PER_SEGMENT' ? 'Per segment' : 'Per SMS';
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
        ...fromLazyEvent(event, { rows: this.pageSize, sortField: 'minimumSmsVolume', sortOrder: 1 }),
        query: this.query().trim(),
        status: this.statusFilter() === 'all' ? undefined : this.statusFilter(),
      });
      this.profiles.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.activeCount.set(page.activeCount ?? 0);
      this.inactiveCount.set(page.inactiveCount ?? 0);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load pricing profiles.');
    } finally {
      this.loading.set(false);
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
