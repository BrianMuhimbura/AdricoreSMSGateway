import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PartnersApi } from '../partners/partners.api';
import { Partner, PartnerCredit, PartnerPlanCatalog } from '../partners/partners.model';

@Component({
  selector: 'app-partner-profile',
  imports: [DatePipe],
  templateUrl: './partner-profile.html',
  styleUrl: './partner-profile.css',
})
export class PartnerProfile implements OnInit {
  private readonly api = inject(PartnersApi);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly partner = signal<Partner | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  refresh(): void {
    void this.load();
  }

  formatMoney(value: number | string | null | undefined, currency?: string | null): string {
    if (value == null || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(amount)) {
      return String(value);
    }
    const code = currency || this.partner()?.currency || this.credit()?.currency || 'UGX';
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

  credit(): PartnerCredit | null {
    return this.partner()?.credit ?? null;
  }

  remainingSms(): number | null {
    const price = Number(this.partner()?.pricingProfile?.sellingPricePerSms);
    const credit = Number(this.credit()?.available ?? this.credit()?.balance);
    if (!Number.isFinite(price) || price <= 0 || !Number.isFinite(credit)) {
      return null;
    }
    return Math.max(0, Math.floor(credit / price));
  }

  plans(): PartnerPlanCatalog[] {
    return this.partner()?.pricingCatalog ?? [];
  }

  isCurrentPlan(plan: PartnerPlanCatalog): boolean {
    const current = this.partner()?.pricingProfile;
    return current?.id === plan.id || current?.profileCode === plan.profileCode;
  }

  volumeRange(plan: PartnerPlanCatalog): string {
    const min = plan.minimumSmsVolume ?? 0;
    if (plan.maximumSmsVolume == null) {
      return `${min.toLocaleString()}+ SMS`;
    }
    return `${min.toLocaleString()} – ${plan.maximumSmsVolume.toLocaleString()} SMS`;
  }

  creditRange(plan: PartnerPlanCatalog): string {
    const currency = plan.currencyCode || this.partner()?.currency;
    const min = this.formatMoney(plan.minimumCredit, currency);
    if (plan.maximumCredit == null || plan.maximumCredit === '') {
      return `${min}+`;
    }
    return `${min} – ${this.formatMoney(plan.maximumCredit, currency)}`;
  }

  billingLabel(type?: string | null): string {
    return type === 'PER_SEGMENT' ? 'Per segment' : 'Per SMS';
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.partner.set(await this.api.profile());
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load partner profile.');
    } finally {
      this.loading.set(false);
    }
  }
}
