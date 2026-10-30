import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Account, Api, errorMessage, Transaction } from './api';

@Component({
  imports: [FormsModule, DecimalPipe, DatePipe],
  template: `
    <h1>Transactions</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    <section class="card">
      <h2>Nouveau paiement</h2>
      <form class="row" (ngSubmit)="pay()">
        <label>Compte
          <select name="accountId" [(ngModel)]="payment.accountId" required>
            @for (account of accounts(); track account.id) {
              <option [value]="account.id">{{ label(account) }}</option>
            }
          </select>
        </label>
        <label>Montant
          <input name="amount" type="number" min="0.01" step="0.01" [(ngModel)]="payment.amount" required />
        </label>
        <label>Devise
          <input name="currency" [(ngModel)]="payment.currency" maxlength="3" required />
        </label>
        <label>Catégorie
          <input name="merchantCategory" [(ngModel)]="payment.merchantCategory" required />
        </label>
        <label>Pays
          <input name="country" [(ngModel)]="payment.country" maxlength="2" required />
        </label>
        <button type="submit">Payer</button>
      </form>
    </section>

    <section class="card">
      <h2>Virement</h2>
      <form class="row" (ngSubmit)="sendTransfer()">
        <label>Depuis
          <select name="sourceAccountId" [(ngModel)]="transfer.sourceAccountId" required>
            @for (account of accounts(); track account.id) {
              <option [value]="account.id">{{ label(account) }}</option>
            }
          </select>
        </label>
        <label>Vers
          <select name="targetAccountId" [(ngModel)]="transfer.targetAccountId" required>
            @for (account of accounts(); track account.id) {
              <option [value]="account.id">{{ label(account) }}</option>
            }
          </select>
        </label>
        <label>Montant
          <input name="transferAmount" type="number" min="0.01" step="0.01" [(ngModel)]="transfer.amount" required />
        </label>
        <label>Devise
          <input name="transferCurrency" [(ngModel)]="transfer.currency" maxlength="3" required />
        </label>
        <label>Pays
          <input name="transferCountry" [(ngModel)]="transfer.country" maxlength="2" required />
        </label>
        <button type="submit">Virer</button>
      </form>
    </section>

    <section class="card">
      <label>Filtrer par compte
        <select name="filter" [(ngModel)]="filter" (ngModelChange)="load()">
          <option value="">Tous les comptes</option>
          @for (account of accounts(); track account.id) {
            <option [value]="account.id">{{ label(account) }}</option>
          }
        </select>
      </label>
      @if (transactions().length === 0) {
        <p class="muted">Aucune transaction.</p>
      } @else {
        <table>
          <thead>
            <tr><th>Date</th><th>Compte</th><th>Sens</th><th class="num">Montant</th><th>Catégorie</th><th>Pays</th></tr>
          </thead>
          <tbody>
            @for (tx of transactions(); track tx.id) {
              <tr>
                <td>{{ tx.createdAt | date: 'short' }}</td>
                <td><code>{{ tx.accountId }}</code></td>
                <td>{{ tx.type === 'DEBIT' ? 'Débit' : 'Crédit' }}</td>
                <td class="num">{{ tx.amount | number: '1.2-2' }} {{ tx.currency }}</td>
                <td>{{ tx.merchantCategory }}</td>
                <td>{{ tx.country }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
})
export class TransactionsPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly error = signal('');
  protected filter = '';
  protected payment = { accountId: '', amount: 10, currency: 'EUR', merchantCategory: 'GROCERY', country: 'MA' };
  protected transfer = { sourceAccountId: '', targetAccountId: '', amount: 10, currency: 'EUR', country: 'MA' };

  ngOnInit(): void {
    this.api.accounts().subscribe({
      next: (accounts) => this.accounts.set(accounts),
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.load();
  }

  protected label(account: Account): string {
    return `${account.customerReference} (${account.currency}) - ${account.id.slice(0, 8)}`;
  }

  protected pay(): void {
    this.error.set('');
    this.api.createTransaction(this.payment).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected sendTransfer(): void {
    this.error.set('');
    this.api.transfer(this.transfer).subscribe({
      next: () => this.load(),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected load(): void {
    this.api.transactions(this.filter).subscribe({
      next: (transactions) => this.transactions.set(transactions),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
