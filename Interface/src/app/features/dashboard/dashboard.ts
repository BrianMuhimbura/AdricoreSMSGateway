import { Component, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/auth.models';
import { AuditLogsApi } from '../audit-logs/audit-logs.api';
import { AuditLogEntry } from '../audit-logs/audit-logs.model';
import { PartnersApi } from '../partners/partners.api';
import { Partner, PartnerPaymentRequest, PartnerTopupResult } from '../partners/partners.model';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, ReactiveFormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly auditLogs = inject(AuditLogsApi);
  private readonly partnersApi = inject(PartnersApi);
  private readonly fb = inject(FormBuilder);

  readonly canViewAudit = signal(false);
  readonly isPartnerUser = signal(false);
  readonly auditLoading = signal(false);
  readonly auditError = signal<string | null>(null);
  readonly recentLogs = signal<AuditLogEntry[]>([]);
  readonly auditTotal = signal(0);
  readonly auditToday = signal(0);
  readonly auditFailed = signal(0);
  readonly partner = signal<Partner | null>(null);
  readonly topupOpen = signal(false);
  readonly topupSaving = signal(false);
  readonly paying = signal(false);
  readonly topupError = signal<string | null>(null);
  readonly topupResult = signal<PartnerTopupResult | null>(null);

  readonly topupForm = this.fb.nonNullable.group({
    amount: [10000, [Validators.required, Validators.min(1)]],
  });

  readonly payForm = this.fb.nonNullable.group({
    customerPhone: ['', [Validators.required, Validators.minLength(9)]],
  });

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private pollCount = 0;

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

  ngOnDestroy(): void {
    this.stopPaymentPoll();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.topupSaving() || this.paying()) {
      return;
    }
    this.closeTopup();
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

  creditAmount(): number | string | null {
    return this.partner()?.credit?.available ?? this.partner()?.credit?.balance ?? null;
  }

  creditLow(): boolean {
    const value = Number(this.creditAmount() ?? 0);
    return !Number.isNaN(value) && value <= 0;
  }

  pendingTopup(): number {
    const value = Number(this.partner()?.credit?.pendingTopup ?? 0);
    return Number.isNaN(value) ? 0 : value;
  }

  reservedAmount(): number {
    const value = Number(this.partner()?.credit?.reserved ?? 0);
    return Number.isNaN(value) ? 0 : value;
  }

  planName(): string {
    return this.partner()?.pricingProfile?.profileName || this.partner()?.pricingProfileName || 'Starter Profile';
  }

  planCode(): string {
    return this.partner()?.pricingProfile?.profileCode || this.partner()?.pricingProfileCode || 'STARTER';
  }

  planPrice(): number | string | null {
    return this.partner()?.pricingProfile?.sellingPricePerSms ?? null;
  }

  planCurrency(): string {
    return this.partner()?.pricingProfile?.currencyCode || this.partner()?.currency || 'UGX';
  }

  planBilling(): string {
    return this.partner()?.pricingProfile?.billingType === 'PER_SEGMENT' ? 'Billed per segment' : 'Billed per SMS';
  }

  planVolume(): string {
    const profile = this.partner()?.pricingProfile;
    const min = profile?.minimumSmsVolume ?? 0;
    if (profile?.maximumSmsVolume == null) {
      return `${min.toLocaleString()}+ SMS volume`;
    }
    return `${min.toLocaleString()} – ${profile.maximumSmsVolume.toLocaleString()} SMS volume`;
  }

  remainingSms(): number | null {
    const price = Number(this.planPrice());
    const credit = Number(this.creditAmount());
    if (!Number.isFinite(price) || price <= 0 || !Number.isFinite(credit)) {
      return null;
    }
    return Math.max(0, Math.floor(credit / price));
  }

  invoiceId(): string | null {
    const result = this.topupResult();
    return result?.invoice?.id || result?.invoiceId || null;
  }

  payment(): PartnerPaymentRequest | null {
    return this.topupResult()?.payment ?? null;
  }

  paymentStatus(): string {
    return (this.payment()?.status || '').toUpperCase();
  }

  paymentInFlight(): boolean {
    const status = this.paymentStatus();
    return status === 'PENDING' || status === 'REQUESTED';
  }

  paymentSuccessful(): boolean {
    return this.paymentStatus() === 'SUCCESSFUL';
  }

  paymentFailed(): boolean {
    return this.paymentStatus() === 'FAILED';
  }

  openTopup(): void {
    this.stopPaymentPoll();
    this.topupError.set(null);
    this.topupResult.set(null);
    this.topupForm.reset({ amount: 10000 });
    this.payForm.reset({ customerPhone: this.partner()?.phone || '' });
    this.topupOpen.set(true);
  }

  closeTopup(): void {
    if (this.topupSaving() || this.paying()) {
      return;
    }
    this.stopPaymentPoll();
    this.topupOpen.set(false);
    this.topupError.set(null);
    this.topupResult.set(null);
  }

  async submitTopup(): Promise<void> {
    if (this.topupForm.invalid) {
      this.topupForm.markAllAsTouched();
      return;
    }
    this.topupSaving.set(true);
    this.topupError.set(null);
    try {
      const result = await this.partnersApi.requestTopup({
        amount: Number(this.topupForm.controls.amount.value),
      });
      this.topupResult.set(result);
      if (!this.payForm.controls.customerPhone.value) {
        this.payForm.patchValue({ customerPhone: this.partner()?.phone || '' });
      }
      await this.loadPartner();
    } catch (error) {
      this.topupError.set(error instanceof Error ? error.message : 'Unable to create a top-up request.');
    } finally {
      this.topupSaving.set(false);
    }
  }

  async submitPayment(): Promise<void> {
    const invoiceId = this.invoiceId();
    if (!invoiceId) {
      this.topupError.set('Create a top-up invoice first.');
      return;
    }
    if (this.payForm.invalid) {
      this.payForm.markAllAsTouched();
      return;
    }
    this.paying.set(true);
    this.topupError.set(null);
    try {
      const result = await this.partnersApi.payNow({
        invoiceId,
        customerPhone: this.payForm.controls.customerPhone.value,
      });
      this.topupResult.set(result);
      if (this.paymentInFlight()) {
        this.startPaymentPoll(result.payment?.id);
      } else if (this.paymentSuccessful()) {
        await this.loadPartner();
      }
    } catch (error) {
      this.topupError.set(error instanceof Error ? error.message : 'Unable to start the mobile money payment.');
    } finally {
      this.paying.set(false);
    }
  }

  private async checkPayment(paymentRequestId?: string): Promise<void> {
    const id = paymentRequestId || this.payment()?.id;
    if (!id) {
      this.stopPaymentPoll();
      return;
    }
    this.pollCount += 1;
    if (this.pollCount > 90) {
      this.stopPaymentPoll();
      this.topupError.set('Still waiting for confirmation. Keep this page open or try Pay now again shortly.');
      return;
    }
    try {
      const result = await this.partnersApi.paymentStatus(id);
      this.topupResult.set(result);
      if (this.paymentSuccessful()) {
        this.stopPaymentPoll();
        this.topupError.set(null);
        await this.loadPartner();
      } else if (this.paymentFailed()) {
        this.stopPaymentPoll();
      }
    } catch (error) {
      this.topupError.set(error instanceof Error ? error.message : 'Unable to check payment status.');
    }
  }

  private startPaymentPoll(paymentRequestId?: string): void {
    this.stopPaymentPoll();
    if (!paymentRequestId) {
      return;
    }
    this.pollCount = 0;
    this.pollTimer = setInterval(() => void this.checkPayment(paymentRequestId), 4000);
  }

  private stopPaymentPoll(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.pollCount = 0;
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
