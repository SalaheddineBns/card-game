import { Routes } from '@angular/router';
import { ProfileStore } from './profile-store';

export default [
  {
    path: '',
    title: 'Mon profil · Speedest Game',
    providers: [ProfileStore],
    loadComponent: () => import('./profile-page').then((m) => m.ProfilePage),
  },
] satisfies Routes;
