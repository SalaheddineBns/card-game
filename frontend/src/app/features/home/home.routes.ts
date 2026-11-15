import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Salon · Speedest Game',
    loadComponent: () => import('./lobby-page').then((m) => m.LobbyPage),
  },
] satisfies Routes;
