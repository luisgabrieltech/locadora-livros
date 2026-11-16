import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { map, tap, timeout } from 'rxjs';

interface Session {
  usuario: string;
  authorization: string;
  headerName: string;
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  // Login usa o backend HTTP diretamente para nao depender do interceptor da sessao.
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);
  readonly autenticado = computed(() => this.session() !== null);
  readonly usuario = computed(() => this.session()?.usuario ?? '');
  readonly aviso = signal('');

  entrar(usuario: string, senha: string) {
    this.session.set(null);
    this.aviso.set('');
    const bytes = new TextEncoder().encode(`${usuario}:${senha}`);
    const authorization = `Basic ${btoa(Array.from(bytes, (b) => String.fromCharCode(b)).join(''))}`;
    return this.http
      .get<{ headerName: string; token: string }>('/api/v1/csrf', {
        headers: { Authorization: authorization, 'X-Requested-With': 'XMLHttpRequest' },
      })
      .pipe(
        timeout(15000),
        tap(({ headerName, token }) =>
          this.session.set({ usuario, authorization, headerName, token }),
        ),
        map(() => undefined),
      );
  }

  headers(metodo: string): HttpHeaders {
    let headers = new HttpHeaders({ 'X-Requested-With': 'XMLHttpRequest' });
    const session = this.session();
    if (!session) return headers;
    headers = headers.set('Authorization', session.authorization);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(metodo)) {
      headers = headers.set(session.headerName, session.token);
    }
    return headers;
  }

  sair(aviso = '') {
    this.session.set(null);
    this.aviso.set(aviso);
    void this.router.navigateByUrl('/entrar');
  }
}
