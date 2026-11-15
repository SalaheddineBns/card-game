import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CardColor, CardSymbol } from '../../../core/realtime/protocol';

const COLOR_NAMES: Record<CardColor, string> = { red: 'rouge', blue: 'bleu', green: 'vert' };
const COLOR_CLASSES: Record<CardColor, string> = {
  red: 'bg-card-red',
  blue: 'bg-card-blue',
  green: 'bg-card-green',
};

/**
 * A game card: a letter on a coloured face. The colour is also written on the card and in the
 * accessible name, so it never relies on colour alone.
 */
@Component({
  selector: 'app-playing-card',
  templateUrl: './playing-card.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayingCard {
  readonly symbol = input.required<CardSymbol>();
  readonly color = input.required<CardColor>();
  readonly size = input<'md' | 'lg'>('md');

  protected readonly colorName = computed(() => COLOR_NAMES[this.color()]);
  protected readonly ariaLabel = computed(() => `Carte ${this.symbol()} ${this.colorName()}`);
  protected readonly faceClasses = computed(
    () =>
      `${COLOR_CLASSES[this.color()]} ${this.size() === 'lg' ? 'h-40 w-28 text-6xl' : 'h-28 w-20 text-4xl'}`,
  );
}
