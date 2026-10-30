import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { logout, username } from './auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header>
      <strong>Banque</strong>
      <nav>
        <a routerLink="/dashboard" routerLinkActive="active">Tableau de bord</a>
        <a routerLink="/accounts" routerLinkActive="active">Comptes</a>
        <a routerLink="/transactions" routerLinkActive="active">Transactions</a>
        <a routerLink="/assistant" routerLinkActive="active">Assistant</a>
      </nav>
      <span class="spacer"></span>
      <span class="user">{{ user }}</span>
      <button class="secondary" (click)="logout()">Déconnexion</button>
    </header>
    <main><router-outlet /></main>
  `,
  styles: `
    header {
      display: flex; flex-wrap: wrap; align-items: center; gap: 16px;
      padding: 10px 20px; background: var(--surface); border-bottom: 1px solid var(--line);
    }
    nav { display: flex; gap: 12px; }
    nav a { color: var(--muted); text-decoration: none; padding: 4px 2px; }
    nav a.active { color: var(--brand); border-bottom: 2px solid var(--brand); }
    .spacer { flex: 1; }
    .user { color: var(--muted); }
    main { max-width: 1100px; margin: 0 auto; padding: 20px; }
  `,
})
export class App {
  protected readonly user = username();
  protected readonly logout = logout;
}
