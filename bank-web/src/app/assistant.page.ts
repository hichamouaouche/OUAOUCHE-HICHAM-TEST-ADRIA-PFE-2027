import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, errorMessage } from './api';

interface ChatLine {
  author: 'Vous' | 'Assistant';
  text: string;
}

@Component({
  imports: [FormsModule],
  template: `
    <h1>Assistant bancaire</h1>
    <section class="card log" aria-live="polite">
      @if (lines().length === 0) {
        <p class="muted">Exemple : « Quels sont les comptes et leurs soldes ? »</p>
      }
      @for (line of lines(); track $index) {
        <p class="line"><strong>{{ line.author }} :</strong> {{ line.text }}</p>
      }
      @if (pending()) {
        <p class="muted">L'assistant réfléchit…</p>
      }
    </section>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    <form class="row" (ngSubmit)="send()">
      <input name="message" class="grow" aria-label="Votre message" [(ngModel)]="message" autocomplete="off" />
      <button type="submit" [disabled]="pending() || !message.trim()">Envoyer</button>
    </form>
  `,
  styles: `
    .log { min-height: 300px; }
    .line { white-space: pre-wrap; margin: 0 0 10px; }
    .grow { flex: 1; min-width: 200px; }
  `,
})
export class AssistantPage {
  private readonly api = inject(Api);
  private readonly conversationId = crypto.randomUUID();
  protected readonly lines = signal<ChatLine[]>([]);
  protected readonly pending = signal(false);
  protected readonly error = signal('');
  protected message = '';

  protected send(): void {
    const text = this.message.trim();
    if (!text || this.pending()) {
      return;
    }
    this.message = '';
    this.error.set('');
    this.pending.set(true);
    this.lines.update((lines) => [...lines, { author: 'Vous', text }]);
    this.api.chat(this.conversationId, text).subscribe({
      next: ({ reply }) => {
        this.lines.update((lines) => [...lines, { author: 'Assistant', text: reply }]);
        this.pending.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.pending.set(false);
      },
    });
  }
}
