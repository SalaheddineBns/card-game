import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Salon · Speedest Game',
    loadComponent: () => import('./lobby/lobby-page').then((m) => m.LobbyPage),
  },
] satisfies Routes;
