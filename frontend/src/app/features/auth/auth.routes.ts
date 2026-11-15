import { Routes } from '@angular/router';

export default [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    title: 'Connexion · Speedest Game',
    loadComponent: () => import('./login/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Créer un compte · Speedest Game',
    loadComponent: () => import('./register/register-page').then((m) => m.RegisterPage),
  },
] satisfies Routes;
