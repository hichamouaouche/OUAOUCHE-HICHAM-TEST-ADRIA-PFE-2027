import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Account, Api, errorMessage } from './api';
import { isAdmin } from './auth';

@Component({
  imports: [FormsModule, DecimalPipe],
  template: `
    <h1>Comptes bancaires</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (admin) {
      <section class="card">
        <h2>Nouveau compte</h2>
        <form class="row" (ngSubmit)="create()">
          <label>Référence client
            <input name="customerReference" [(ngModel)]="draft.customerReference" required />
          </label>
          <label>Devise
            <input name="currency" [(ngModel)]="draft.currency" maxlength="3" required />
          </label>
          <label>Solde initial
            <input name="initialBalance" type="number" min="0" [(ngModel)]="draft.initialBalance" required />
          </label>
          <button type="submit">Créer</button>
        </form>
      </section>
    }

    <section class="card">
      @if (accounts().length === 0) {
        <p class="muted">Aucun compte pour le moment.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Identifiant</th><th>Client</th><th>Statut</th><th class="num">Solde</th><th>Devise</th>
              @if (admin) { <th>Action</th> }
            </tr>
          </thead>
          <tbody>
            @for (account of accounts(); track account.id) {
              <tr>
                <td><code>{{ account.id }}</code></td>
                <td>{{ account.customerReference }}</td>
                <td>{{ account.status === 'ACTIVE' ? 'Actif' : 'Bloqué' }}</td>
                <td class="num">{{ account.balance | number: '1.2-2' }}</td>
                <td>{{ account.currency }}</td>
                @if (admin) {
                  <td>
                    <button class="secondary" (click)="toggle(account)">
                      {{ account.status === 'ACTIVE' ? 'Bloquer' : 'Activer' }}
                    </button>
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
})
export class AccountsPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly admin = isAdmin();
  protected readonly accounts = signal<Account[]>([]);
  protected readonly error = signal('');
  protected draft = { customerReference: '', currency: 'EUR', initialBalance: 0 };

  ngOnInit(): void {
    this.load();
  }

  protected create(): void {
    this.api.createAccount(this.draft).subscribe({
      next: () => {
        this.draft = { customerReference: '', currency: 'EUR', initialBalance: 0 };
        this.load();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected toggle(account: Account): void {
    const status = account.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE';
    this.api.changeStatus(account.id, status).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  private load(): void {
    this.error.set('');
    this.api.accounts().subscribe({
      next: (accounts) => this.accounts.set(accounts),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
