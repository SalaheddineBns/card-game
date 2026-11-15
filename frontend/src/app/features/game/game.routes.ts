import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Partie · Speedest Game',
    loadComponent: () => import('./game-page').then((m) => m.GamePage),
  },
] satisfies Routes;
