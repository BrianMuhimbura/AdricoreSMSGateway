import { Component, HostListener, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Table, TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { AuthService } from '../../core/auth/auth.service';
import { fromLazyEvent } from '../../core/api/paged-list';
import { PartnersApi, PartnerLookup } from '../partners/partners.api';
import { UsersApi } from './users.api';
import { SystemRole, SystemUser, UserPayload } from './users.model';

type StatusFilter = 'all' | 'active' | 'inactive';

@Component({
  selector: 'app-users',
  imports: [ReactiveFormsModule, FormsModule, DatePipe, TableModule],
  templateUrl: './users.html',
  styleUrl: '../../shared/list-page.css',
})
export class Users implements OnDestroy {
  private readonly api = inject(UsersApi);
  private readonly partnersApi = inject(PartnersApi);
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10, sortField: 'createdAt', sortOrder: -1 };

  @ViewChild('dt') table?: Table;

  readonly users = signal<SystemUser[]>([]);
  readonly roles = signal<SystemRole[]>([]);
  readonly partners = signal<PartnerLookup[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly query = signal('');
  readonly statusFilter = signal<StatusFilter>('all');
  readonly editorOpen = signal(false);
  readonly confirmOpen = signal(false);
  readonly editing = signal<SystemUser | null>(null);
  readonly pending = signal<SystemUser | null>(null);
  readonly totalRecords = signal(0);
  readonly totalCount = signal(0);
  readonly activeCount = signal(0);
  readonly inactiveCount = signal(0);

  readonly pageSize = 10;
  readonly pageSizeOptions = [10, 20, 50];
  readonly filters: { id: StatusFilter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'active', label: 'Active' },
    { id: 'inactive', label: 'Inactive' },
  ];

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.minLength(2)]],
    lastName: ['', [Validators.required, Validators.minLength(2)]],
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    roleCode: ['', Validators.required],
    partnerId: [''],
    password: [''],
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

  displayName(user: SystemUser): string {
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return fullName || user.username;
  }

  roleLabel(roleCode?: string | null): string {
    if (!roleCode) {
      return '—';
    }
    const match = this.roles().find((role) => role.roleCode === roleCode);
    return match?.roleName || roleCode.replaceAll('_', ' ');
  }

  needsPartner(): boolean {
    return !this.auth.isPartner() && this.form.controls.roleCode.value === 'API_USER';
  }

  isSelf(user: SystemUser): boolean {
    return user.id === this.auth.user()?.id;
  }

  async openCreate(): Promise<void> {
    this.editing.set(null);
    this.error.set(null);
    this.form.reset({
      firstName: '',
      lastName: '',
      username: '',
      email: '',
      roleCode: '',
      partnerId: '',
      password: '',
      isActive: true,
    });
    this.form.controls.password.setValidators([Validators.required, Validators.minLength(8)]);
    this.form.controls.password.updateValueAndValidity();
    this.editorOpen.set(true);
    await Promise.all([this.loadRoles(), this.loadPartners()]);
  }

  async openEdit(user: SystemUser): Promise<void> {
    this.editing.set(user);
    this.error.set(null);
    this.form.reset({
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      username: user.username,
      email: user.email ?? '',
      roleCode: user.roleCode ?? '',
      partnerId: user.partnerId ?? '',
      password: '',
      isActive: user.isActive !== false,
    });
    this.form.controls.password.setValidators([Validators.minLength(8)]);
    this.form.controls.password.updateValueAndValidity();
    this.editorOpen.set(true);
    await Promise.all([this.loadRoles(), this.loadPartners()]);
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
  }

  askDeactivate(user: SystemUser): void {
    this.pending.set(user);
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
    const payload: UserPayload = {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      username: value.username.trim(),
      email: value.email.trim(),
      roleCode: value.roleCode,
      isActive: value.isActive,
    };
    if (this.needsPartner()) {
      if (!value.partnerId) {
        this.error.set('Select the partner this API user belongs to.');
        return;
      }
      payload.partnerId = value.partnerId;
    }
    if (value.password.trim()) {
      payload.password = value.password;
    }

    this.saving.set(true);
    this.error.set(null);
    try {
      const current = this.editing();
      if (current) {
        const updated = await this.api.update(current.id, payload);
        this.flash(`Updated ${this.displayName(updated)}.`);
      } else {
        if (!payload.password) {
          this.error.set('Password must be at least 8 characters long.');
          return;
        }
        const created = await this.api.create(payload);
        this.flash(`Created ${this.displayName(created)}.`);
      }
      this.closeEditor();
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to save user.');
    } finally {
      this.saving.set(false);
    }
  }

  async confirmDeactivate(): Promise<void> {
    const user = this.pending();
    if (!user) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.api.update(user.id, { isActive: false });
      this.flash(`${this.displayName(user)} is now inactive.`);
      this.closeConfirm();
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to deactivate user.');
    } finally {
      this.saving.set(false);
    }
  }

  async reactivate(user: SystemUser): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.api.update(user.id, { isActive: true });
      this.flash(`${this.displayName(user)} is active again.`);
      this.refresh();
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to reactivate user.');
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
        isActive: this.statusFilter() === 'all' ? undefined : this.statusFilter() === 'active',
      });
      this.users.set(page.items ?? []);
      this.totalRecords.set(page.totalRecords ?? 0);
      this.totalCount.set(page.totalCount ?? page.totalRecords ?? 0);
      this.activeCount.set(page.activeCount ?? 0);
      this.inactiveCount.set(page.inactiveCount ?? 0);
      if (this.roles().length === 0) {
        void this.loadRoles();
      }
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load users.');
    } finally {
      this.loading.set(false);
    }
  }

  private async loadRoles(): Promise<void> {
    try {
      this.roles.set((await this.api.roles()) ?? []);
    } catch {
      this.roles.set([]);
    }
  }

  private async loadPartners(): Promise<void> {
    if (this.auth.isPartner()) {
      return;
    }
    try {
      this.partners.set((await this.partnersApi.lookup()) ?? []);
    } catch {
      this.partners.set([]);
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
