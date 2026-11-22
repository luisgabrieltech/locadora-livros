import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Entrar | Locadora de livros',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'clientes',
    title: 'Clientes | Locadora de livros',
    canActivate: [authGuard],
    loadComponent: () => import('./clientes/clientes').then((m) => m.Clientes),
  },
  { path: '', pathMatch: 'full', redirectTo: 'clientes' },
  { path: '**', redirectTo: 'clientes' },
];
