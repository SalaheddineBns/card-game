import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Résultat · Speedest Game',
    loadComponent: () => import('./game-result-page').then((m) => m.GameResultPage),
  },
] satisfies Routes;
