import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { apiInterceptor } from './api.interceptor';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('Autenticacao e protecao das requisicoes', () => {
  let auth: AuthService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  function entrar() {
    auth.entrar('operador-teste', 'senha-ficticia').subscribe();
    const request = backend.expectOne('/api/v1/csrf');
    expect(request.request.headers.get('Authorization')).toBe(
      `Basic ${btoa('operador-teste:senha-ficticia')}`,
    );
    expect(request.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    request.flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf-ficticio' });
  }

  it('so autentica apos obter CSRF e nao grava credenciais em storage', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    expect(auth.autenticado()).toBe(false);
    entrar();
    expect(auth.autenticado()).toBe(true);
    expect(auth.usuario()).toBe('operador-teste');
    expect(storage).not.toHaveBeenCalled();
  });

  it('nao autentica quando a API rejeita o login', () => {
    const erro = vi.fn();
    auth.entrar('operador-teste', 'incorreta').subscribe({ error: erro });
    backend.expectOne('/api/v1/csrf').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(erro).toHaveBeenCalledOnce();
    expect(auth.autenticado()).toBe(false);
    expect(auth.headers('POST').has('Authorization')).toBe(false);
  });

  it('envia Basic em leituras e token CSRF nas escritas', () => {
    entrar();
    http.get('/api/v1/clientes').subscribe();
    const leitura = backend.expectOne('/api/v1/clientes');
    expect(leitura.request.headers.has('Authorization')).toBe(true);
    expect(leitura.request.headers.has('X-CSRF-TOKEN')).toBe(false);
    leitura.flush({ items: [] });
    http.post('/api/v1/clientes', { nome: 'Ficticio' }).subscribe();
    const escrita = backend.expectOne('/api/v1/clientes');
    expect(escrita.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-ficticio');
    expect(escrita.request.headers.has('Authorization')).toBe(true);
    escrita.flush({});
  });

  it('nao transmite Basic ou CSRF para dominios externos', () => {
    entrar();
    http.post('https://example.invalid/api/v1/clientes', {}).subscribe();
    const request = backend.expectOne('https://example.invalid/api/v1/clientes');
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(false);
    request.flush({});
  });

  it('propaga falha CSRF sem repetir a escrita nem perder a sessao silenciosamente', () => {
    entrar();
    const erro = vi.fn();
    http.post('/api/v1/clientes', {}).subscribe({ error: erro });
    backend.expectOne('/api/v1/clientes').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(erro).toHaveBeenCalledOnce();
    expect(auth.autenticado()).toBe(true);
    backend.expectNone('/api/v1/clientes');
  });

  it('limpa a memoria ao sair e protege a rota apos recarregar', () => {
    const semLogin = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(semLogin).not.toBe(true);
    entrar();
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))).toBe(true);
    auth.sair();
    expect(auth.headers('POST').has('Authorization')).toBe(false);
    expect(auth.headers('POST').has('X-CSRF-TOKEN')).toBe(false);
    expect(auth.usuario()).toBe('');
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))).not.toBe(true);
  });

  it('encerra acesso em 401, mas preserva a sessao em conflito sem repetir a escrita', () => {
    entrar();
    const erro = vi.fn();
    http.post('/api/v1/clientes', {}).subscribe({ error: erro });
    backend.expectOne('/api/v1/clientes').flush({}, { status: 409, statusText: 'Conflict' });
    expect(auth.autenticado()).toBe(true);
    expect(erro).toHaveBeenCalledOnce();
    http.get('/api/v1/clientes').subscribe({ error: erro });
    backend.expectOne('/api/v1/clientes').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.autenticado()).toBe(false);
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/entrar');
  });
});
