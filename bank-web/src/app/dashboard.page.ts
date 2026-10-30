import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Account, Api, errorMessage, Transaction } from './api';

@Component({
  imports: [DecimalPipe, DatePipe],
  template: `
    <h1>Tableau de bord</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    <section class="tiles" aria-label="Indicateurs clés">
      <div class="card tile"><span class="label">Comptes</span><strong>{{ accounts().length }}</strong></div>
      <div class="card tile"><span class="label">Comptes actifs</span><strong>{{ activeCount() }}</strong></div>
      <div class="card tile"><span class="label">Comptes bloqués</span><strong>{{ blockedCount() }}</strong></div>
      <div class="card tile"><span class="label">Transactions</span><strong>{{ transactions().length }}</strong></div>
      @for (total of balances(); track total.currency) {
        <div class="card tile">
          <span class="label">Solde total {{ total.currency }}</span>
          <strong>{{ total.amount | number: '1.2-2' }}</strong>
        </div>
      }
    </section>

    <div class="columns">
      <section class="card">
        <h2>Transactions par catégorie</h2>
        @if (categories().length === 0) {
          <p class="muted">Aucune transaction pour le moment.</p>
        }
        @for (category of categories(); track category.name) {
          <div class="bar-row" [title]="category.name + ' : ' + category.count + ' transaction(s)'">
            <span class="bar-name">{{ category.name }}</span>
            <span class="bar-track"><span class="bar" [style.width.%]="category.share"></span></span>
            <span class="bar-value">{{ category.count }}</span>
          </div>
        }
      </section>

      <section class="card">
        <h2>Dernières transactions</h2>
        @if (latest().length === 0) {
          <p class="muted">Aucune transaction pour le moment.</p>
        } @else {
          <table>
            <thead>
              <tr><th>Date</th><th>Sens</th><th class="num">Montant</th><th>Catégorie</th></tr>
            </thead>
            <tbody>
              @for (tx of latest(); track tx.id) {
                <tr>
                  <td>{{ tx.createdAt | date: 'short' }}</td>
                  <td>{{ tx.type === 'DEBIT' ? 'Débit' : 'Crédit' }}</td>
                  <td class="num">{{ tx.amount | number: '1.2-2' }} {{ tx.currency }}</td>
                  <td>{{ tx.merchantCategory }}</td>
                </tr>
              }
            </tbody>
          </table>
        }
      </section>
    </div>
  `,
  styles: `
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
    .tile { display: flex; flex-direction: column; gap: 4px; }
    .tile .label { color: var(--muted); font-size: 13px; }
    .tile strong { font-size: 24px; font-variant-numeric: tabular-nums; }
    .columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 16px; align-items: start; }
    .bar-row { display: grid; grid-template-columns: 110px 1fr 32px; align-items: center; gap: 8px; padding: 5px 0; }
    .bar-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; }
    .bar-track { display: block; height: 10px; }
    .bar { display: block; height: 100%; min-width: 2px; background: var(--brand); border-radius: 0 4px 4px 0; }
    .bar-value { text-align: right; font-variant-numeric: tabular-nums; }
  `,
})
export class DashboardPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly error = signal('');

  protected readonly activeCount = computed(() => this.accounts().filter((a) => a.status === 'ACTIVE').length);
  protected readonly blockedCount = computed(() => this.accounts().filter((a) => a.status === 'BLOCKED').length);
  protected readonly latest = computed(() => this.transactions().slice(0, 6));

  /** Un total par devise : additionner des devises différentes n'aurait pas de sens. */
  protected readonly balances = computed(() => {
    const totals = new Map<string, number>();
    for (const account of this.accounts()) {
      totals.set(account.currency, (totals.get(account.currency) ?? 0) + account.balance);
    }
    return [...totals].map(([currency, amount]) => ({ currency, amount }));
  });

  protected readonly categories = computed(() => {
    const counts = new Map<string, number>();
    for (const tx of this.transactions()) {
      counts.set(tx.merchantCategory, (counts.get(tx.merchantCategory) ?? 0) + 1);
    }
    const max = Math.max(1, ...counts.values());
    return [...counts]
      .sort((a, b) => b[1] - a[1])
      .map(([name, count]) => ({ name, count, share: (count / max) * 100 }));
  });

  ngOnInit(): void {
    forkJoin({ accounts: this.api.accounts(), transactions: this.api.transactions('') }).subscribe({
      next: ({ accounts, transactions }) => {
        this.accounts.set(accounts);
        this.transactions.set(transactions);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }
}
