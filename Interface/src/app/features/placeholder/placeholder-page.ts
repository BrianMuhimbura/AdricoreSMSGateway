import { Component, input } from '@angular/core';

@Component({
  selector: 'app-placeholder-page',
  template: `
    <section class="page">
      <p class="kicker">Coming next</p>
      <h2>{{ title() }}</h2>
      <p>{{ description() }}</p>
    </section>
  `,
  styles: `
    .page {
      background: white;
      border: 1px solid var(--color-line);
      border-radius: 1.25rem;
      padding: 1.4rem 1.25rem;
      box-shadow: var(--shadow-card);
    }
    .kicker {
      margin: 0;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.16em;
      text-transform: uppercase;
      color: var(--color-teal-dark);
    }
    h2 {
      margin: 0.25rem 0 0.5rem;
      font-family: var(--font-display);
      color: var(--color-navy);
    }
    p:last-child {
      margin: 0;
      color: var(--color-muted);
      max-width: 40rem;
    }
  `,
})
export class PlaceholderPage {
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
