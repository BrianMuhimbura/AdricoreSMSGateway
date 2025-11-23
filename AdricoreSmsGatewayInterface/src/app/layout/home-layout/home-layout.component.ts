import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { PanelMenuModule } from 'primeng/panelmenu';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-home-layout',
  standalone: true,
  imports: [RouterOutlet, CommonModule, PanelMenuModule, ButtonModule],
  templateUrl: './home-layout.component.html'
})
export class HomeLayoutComponent {
  pageTitle = 'Dashboard';
  items: MenuItem[] = [
    { label: 'Dashboard', icon: 'pi pi-chart-bar', routerLink: ['/home'] },
    { label: 'Partners', icon: 'pi pi-users', routerLink: ['/home/partners'] },
    { label: 'Transactions', icon: 'pi pi-exchange', routerLink: ['/home/transactions'] },
    { label: 'Messages', icon: 'pi pi-envelope', routerLink: ['/home/messages'] }
  ];

  constructor(public auth: AuthService) {}

  onActivate(component: any) {
    if (component?.title) {
      this.pageTitle = component.title;
    }
  }

  logout() {
    this.auth.logout();
  }
}

