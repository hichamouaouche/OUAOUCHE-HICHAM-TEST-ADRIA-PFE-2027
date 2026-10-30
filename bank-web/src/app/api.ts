import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from './environment';

export interface Account {
  id: string;
  customerReference: string;
  status: 'ACTIVE' | 'BLOCKED';
  currency: string;
  balance: number;
}

export interface Transaction {
  id: string;
  accountId: string;
  amount: number;
  currency: string;
  merchantCategory: string;
  country: string;
  createdAt: string;
  type: 'DEBIT' | 'CREDIT';
  transferId: string | null;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api`;

  accounts() {
    return this.http.get<Account[]>(`${this.base}/accounts`);
  }

  createAccount(body: { customerReference: string; currency: string; initialBalance: number }) {
    return this.http.post<Account>(`${this.base}/accounts`, body);
  }

  changeStatus(id: string, status: Account['status']) {
    return this.http.patch<Account>(`${this.base}/accounts/${id}/status`, { status });
  }

  transactions(accountId: string) {
    const query = accountId ? `?accountId=${accountId}` : '';
    return this.http.get<Transaction[]>(`${this.base}/transactions${query}`);
  }

  createTransaction(body: {
    accountId: string;
    amount: number;
    currency: string;
    merchantCategory: string;
    country: string;
  }) {
    return this.http.post<Transaction>(`${this.base}/transactions`, body);
  }

  transfer(body: {
    sourceAccountId: string;
    targetAccountId: string;
    amount: number;
    currency: string;
    country: string;
  }) {
    return this.http.post<Transaction[]>(`${this.base}/transfers`, body);
  }

  chat(conversationId: string, message: string) {
    return this.http.post<{ reply: string }>(`${this.base}/assistant/chat`, { conversationId, message });
  }
}

export function errorMessage(error: HttpErrorResponse): string {
  return error.error?.detail ?? `Erreur ${error.status} : le service n'a pas pu traiter la demande.`;
}
