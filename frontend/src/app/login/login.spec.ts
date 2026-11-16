import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Tela de login', () => {
  let fixture: ComponentFixture<Login>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Login);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function preencher(id: string, valor: string) {
    const input = element.querySelector<HTMLInputElement>('#' + id)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }
  function submeter() {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  it('nao envia formulario vazio ou usuario composto apenas por espacos', () => {
    submeter();
    expect(element.textContent).toContain('Informe seu usuário.');
    expect(element.textContent).toContain('Informe sua senha.');
    preencher('usuario', '   ');
    preencher('senha', 'ficticia');
    submeter();
    expect(element.querySelector('#usuario')!.getAttribute('aria-invalid')).toBe('true');
    http.expectNone('/api/v1/csrf');
  });

  it('informa API indisponivel e libera uma nova tentativa', () => {
    preencher('usuario', 'operador-teste');
    preencher('senha', 'ficticia');
    submeter();
    http.expectOne('/api/v1/csrf').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'Não foi possível conectar à API.',
    );
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });

  it('mostra credenciais invalidas, limpa a senha e permite nova tentativa', () => {
    preencher('usuario', 'operador-teste');
    preencher('senha', 'incorreta');
    submeter();
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    http.expectOne('/api/v1/csrf').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'Usuário ou senha inválidos.',
    );
    expect(element.querySelector<HTMLInputElement>('#senha')!.value).toBe('');
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });
});
