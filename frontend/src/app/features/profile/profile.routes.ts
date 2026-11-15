import { Routes } from '@angular/router';

export default [
  {
    path: '',
    title: 'Mon profil · Speedest Game',
    loadComponent: () => import('./profile-page/profile-page').then((m) => m.ProfilePage),
  },
] satisfies Routes;
