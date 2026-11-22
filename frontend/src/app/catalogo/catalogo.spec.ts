import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Catalogo } from './catalogo';

const livro = { id: 'livro-1', titulo: 'Livro de teste', autor: 'Autora', isbn: '9780140328721' };
const exemplar = { id: 'copia-1', livroId: livro.id, codigo: 'EX-001', disponivel: false };

function pagina<T>(items: T[]) {
  return {
    items,
    page: 0,
    size: 10,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
  };
}

describe('Tela do catálogo', () => {
  let fixture: ComponentFixture<Catalogo>;
  let backend: HttpTestingController;
  let element: HTMLElement;
  let dialog: HTMLDialogElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Catalogo],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuario: () => 'teste', sair: vi.fn() } },
      ],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Catalogo);
    element = fixture.nativeElement;
    fixture.detectChanges();
    dialog = element.querySelector('dialog')!;
    dialog.showModal = () => {
      dialog.open = true;
    };
    dialog.close = () => {
      dialog.open = false;
    };
    backend.expectOne('/api/v1/livros?page=0&size=10').flush(pagina([livro]));
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function clicar(texto: string) {
    const button = [...element.querySelectorAll('button')].find(
      (item) => item.textContent?.trim() === texto,
    );
    expect(button).toBeDefined();
    button!.click();
    fixture.detectChanges();
  }

  function preencher(id: string, valor: string) {
    const input = element.querySelector<HTMLInputElement>('#' + id)!;
    input.value = valor;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
  }

  function submeter() {
    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }

  function abrirExemplares() {
    clicar('Exemplares');
    backend.expectOne('/api/v1/exemplares?page=0&size=10').flush(pagina([exemplar]));
    backend.expectOne('/api/v1/livros/livro-1').flush(livro);
    fixture.detectChanges();
  }

  it('remove o ISBN na edição e recarrega a lista após salvar', () => {
    clicar('Editar');
    preencher('isbn', '');
    submeter();
    const request = backend.expectOne({ method: 'PUT', url: '/api/v1/livros/livro-1' });
    expect(request.request.body).toEqual({ titulo: livro.titulo, autor: livro.autor, isbn: null });
    request.flush({ ...livro, isbn: null });
    backend.expectOne('/api/v1/livros?page=0&size=10').flush(pagina([{ ...livro, isbn: null }]));
    fixture.detectChanges();
    expect(dialog.open).toBe(false);
    expect(element.textContent).toContain('Não informado');
  });

  it('apresenta a rejeição de ISBN sem perder os dados do livro', () => {
    clicar('Editar');
    preencher('isbn', '123');
    submeter();
    backend
      .expectOne({ method: 'PUT', url: '/api/v1/livros/livro-1' })
      .flush(
        { errors: { isbn: 'Informe um ISBN válido.' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('#isbn-erro')?.textContent).toContain('Informe um ISBN válido.');
    expect(dialog.querySelector<HTMLInputElement>('#isbn')?.value).toBe('123');
  });

  it('seleciona um livro em outra página e mantém o formulário após código duplicado', () => {
    abrirExemplares();
    clicar('Novo exemplar');
    preencher('codigo', exemplar.codigo);
    clicar('Escolher livro');
    backend.expectOne('/api/v1/livros?page=0&size=10').flush({
      ...pagina([livro]),
      totalElements: 11,
      totalPages: 2,
    });
    fixture.detectChanges();
    dialog.querySelector<HTMLButtonElement>('[aria-label="Próxima página de livros"]')!.click();
    const outro = { ...livro, id: 'livro-2' };
    backend.expectOne('/api/v1/livros?page=1&size=10').flush({
      ...pagina([outro]),
      page: 1,
      totalElements: 11,
      totalPages: 2,
    });
    fixture.detectChanges();
    clicar(`${outro.titulo} · ${outro.autor} · ${outro.isbn}`);
    submeter();
    submeter();
    const request = backend.expectOne({ method: 'POST', url: '/api/v1/exemplares' });
    expect(request.request.body).toEqual({ codigo: exemplar.codigo, livroId: outro.id });
    request.flush(
      { detail: 'Já existe um exemplar com este código.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('este código');
    expect(dialog.querySelector<HTMLInputElement>('#codigo')?.value).toBe(exemplar.codigo);
  });

  it('mostra a disponibilidade e conserva a edição quando o vínculo tem histórico', () => {
    abrirExemplares();
    expect(element.querySelector('tbody')?.textContent).toContain('Indisponível');
    expect(element.querySelector('tbody')?.textContent).toContain(livro.titulo);
    clicar('Editar');
    const outro = { ...livro, id: 'livro-2', titulo: 'Outro livro' };
    clicar('Escolher livro');
    backend.expectOne('/api/v1/livros?page=0&size=10').flush(pagina([outro]));
    fixture.detectChanges();
    clicar(`${outro.titulo} · ${outro.autor} · ${outro.isbn}`);
    submeter();
    const request = backend.expectOne({ method: 'PUT', url: '/api/v1/exemplares/copia-1' });
    expect(request.request.body).toEqual({ codigo: exemplar.codigo, livroId: outro.id });
    request.flush(
      { detail: 'Não é permitido trocar o livro de um exemplar com histórico de locação.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('[role="alert"]')?.textContent).toContain('histórico de locação');
  });
});
