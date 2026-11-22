import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Clientes } from './clientes';

const id = '00000000-0000-0000-0000-000000000001';
const resumo = { id, nome: 'Cliente Ficticio', telefone: '11999990000', email: null };
const detalhe = { ...resumo, cpf: '00000000191' };

describe('Tela de clientes', () => {
  let fixture: ComponentFixture<Clientes>;
  let backend: HttpTestingController;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Clientes],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuario: () => 'teste', sair: vi.fn() } },
      ],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Clientes);
    element = fixture.nativeElement;
    fixture.detectChanges();
    const dialog = element.querySelector('dialog')!;
    dialog.showModal = () => {
      dialog.open = true;
    };
    dialog.close = () => {
      dialog.open = false;
    };
  });
  afterEach(() => backend.verify());

  function lista(items: (typeof resumo)[] = [], total = items.length) {
    backend
      .expectOne((r) => r.url === '/api/v1/clientes' && r.method === 'GET')
      .flush({ items, page: 0, size: 10, totalElements: total, totalPages: Math.ceil(total / 10) });
    fixture.detectChanges();
  }
  function botao(texto: string) {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes(texto),
    )!;
  }
  function preencher(campo: string, valor: string) {
    const input = element.querySelector<HTMLInputElement>('#' + campo)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }
  function submeter() {
    element
      .querySelector('dialog form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }
  function novoValido() {
    botao('Novo cliente').click();
    fixture.detectChanges();
    preencher('nome', 'Cliente Ficticio');
    preencher('telefone', '11999990000');
    preencher('cpf', '00000000191');
  }

  it('envia a pagina e o tamanho escolhidos sem filtrar os dados em memoria', () => {
    lista(
      Array.from({ length: 10 }, (_, i) => ({ ...resumo, id: String(i) })),
      21,
    );
    element.querySelector<HTMLButtonElement>('[aria-label="Próxima página"]')!.click();
    const proxima = backend.expectOne(
      (r) =>
        r.url === '/api/v1/clientes' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10',
    );
    proxima.flush({ items: [resumo], page: 1, size: 10, totalElements: 21, totalPages: 3 });
    fixture.detectChanges();
    expect(element.querySelector('.page-counter')!.textContent).toContain('2 de 3');
    const select = element.querySelector<HTMLSelectElement>('#tamanho')!;
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    const maior = backend.expectOne(
      (r) =>
        r.url === '/api/v1/clientes' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '50',
    );
    maior.flush({ items: [resumo], page: 0, size: 50, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    expect(element.querySelector('.page-counter')!.textContent).toContain('1 de 1');
  });

  it('mostra estado vazio e nao envia cadastro incompleto', () => {
    lista();
    expect(element.textContent).toContain('Nenhum cliente cadastrado.');
    botao('Novo cliente').click();
    fixture.detectChanges();
    submeter();
    expect(element.textContent).toContain('Campo obrigatório.');
    backend.expectNone((r) => r.method === 'POST');
  });

  it('lista sem CPF e busca o detalhe somente para edicao', () => {
    lista([resumo]);
    expect(element.querySelector('table')!.textContent).not.toContain(detalhe.cpf);
    botao('Editar').click();
    backend.expectOne('/api/v1/clientes/' + id).flush(detalhe);
    fixture.detectChanges();
    expect(element.querySelector<HTMLInputElement>('#cpf')!.value).toBe(detalhe.cpf);
    expect(element.querySelector('dialog')!.open).toBe(true);
  });

  it('cria cliente, evita submit duplo e recarrega a pagina', () => {
    lista();
    novoValido();
    submeter();
    const post = backend.expectOne({ method: 'POST', url: '/api/v1/clientes' });
    expect(post.request.body).toEqual({
      nome: detalhe.nome,
      telefone: detalhe.telefone,
      cpf: detalhe.cpf,
      email: null,
    });
    submeter();
    backend.expectNone((r) => r.method === 'POST');
    post.flush(detalhe);
    lista([resumo]);
    expect(element.textContent).toContain('Cliente cadastrado com sucesso.');
    expect(element.querySelector('dialog')!.open).toBe(false);
  });

  it('preserva formulario e mostra conflito de CPF sem fechar o dialogo', () => {
    lista();
    novoValido();
    submeter();
    backend.expectOne('/api/v1/clientes').flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(element.querySelector('dialog')!.textContent).toContain(
      'Já existe um cliente com este CPF.',
    );
    expect(element.querySelector<HTMLInputElement>('#nome')!.value).toBe('Cliente Ficticio');
    expect(element.querySelector('dialog')!.open).toBe(true);
    expect(botao('Salvar cliente').disabled).toBe(false);
  });

  it('apresenta validacao de negocio retornada pela API no campo', () => {
    lista();
    novoValido();
    submeter();
    backend
      .expectOne('/api/v1/clientes')
      .flush({ errors: { cpf: 'CPF inválido.' } }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(element.querySelector('#cpf-error')!.textContent).toContain('CPF inválido.');
    expect(element.querySelector('#cpf')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('atualiza todos os campos e permite remover o email', () => {
    lista([resumo]);
    botao('Editar').click();
    backend
      .expectOne('/api/v1/clientes/' + id)
      .flush({ ...detalhe, email: 'teste@example.invalid' });
    fixture.detectChanges();
    preencher('nome', 'Cliente Atualizado');
    preencher('email', '');
    submeter();
    const put = backend.expectOne({ method: 'PUT', url: '/api/v1/clientes/' + id });
    expect(put.request.body.email).toBe(null);
    expect(put.request.body.cpf).toBe(detalhe.cpf);
    expect(put.request.body.nome).toBe('Cliente Atualizado');
    put.flush({ ...detalhe, nome: 'Cliente Atualizado' });
    lista([{ ...resumo, nome: 'Cliente Atualizado' }]);
    expect(element.textContent).toContain('Dados do cliente atualizados.');
  });

  it('navega paginas e reinicia a pagina ao mudar o tamanho', () => {
    lista(
      Array.from({ length: 10 }, (_, i) => ({ ...resumo, id: id + i, nome: 'Ficticio ' + i })),
      11,
    );
    element.querySelector<HTMLButtonElement>('[aria-label="Próxima página"]')!.click();
    const request = backend.expectOne((r) => r.params.get('page') === '1');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ items: [resumo], page: 1, size: 10, totalElements: 11, totalPages: 2 });
    fixture.detectChanges();
    const select = element.querySelector<HTMLSelectElement>('#tamanho')!;
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    const resized = backend.expectOne(
      (r) => r.params.get('page') === '0' && r.params.get('size') === '50',
    );
    resized.flush({ items: [resumo], page: 0, size: 50, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
    expect(
      element.querySelector<HTMLButtonElement>('[aria-label="Próxima página"]')!.disabled,
    ).toBe(true);
  });

  it('mostra falha de listagem e permite tentar novamente', () => {
    backend
      .expectOne((r) => r.url === '/api/v1/clientes')
      .flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element.querySelector('[role="alert"]')!.textContent).toContain(
      'Não foi possível concluir',
    );
    botao('Tentar novamente').click();
    lista([resumo]);
    expect(element.textContent).toContain(resumo.nome);
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });
});
