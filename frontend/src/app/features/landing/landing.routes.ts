import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Speedest Game',
    loadComponent: () => import('./landing-page').then((m) => m.LandingPage),
  },
] satisfies Routes;
