import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

// URLs follow docs/migration-plan.md (rows 4, 7, 10, 14).
export const routes: Routes = [
  { path: '', pathMatch: 'full', loadChildren: () => import('./features/landing/landing.routes') },
  { path: 'auth', loadChildren: () => import('./features/auth/auth.routes') },
  {
    path: '',
    canMatch: [authGuard],
    children: [
      { path: 'lobby', loadChildren: () => import('./features/home/home.routes') },
      { path: 'games/:id/result', loadChildren: () => import('./features/result/result.routes') },
      { path: 'games/:id', loadChildren: () => import('./features/game/game.routes') },
      { path: 'profile', loadChildren: () => import('./features/profile/profile.routes') },
    ],
  },
  { path: '**', redirectTo: '' },
];
