import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, Routes } from '@angular/router';
import { AccountsPage } from './accounts.page';
import { AssistantPage } from './assistant.page';
import { authInterceptor } from './auth';
import { DashboardPage } from './dashboard.page';
import { TransactionsPage } from './transactions.page';

const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardPage, title: 'Tableau de bord' },
  { path: 'accounts', component: AccountsPage, title: 'Comptes' },
  { path: 'transactions', component: TransactionsPage, title: 'Transactions' },
  { path: 'assistant', component: AssistantPage, title: 'Assistant' },
];

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
