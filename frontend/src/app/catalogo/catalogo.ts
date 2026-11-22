import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, finalize, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { mensagemErro, ProblemaApi } from '../core/api-error';
import { Sidebar } from '../sidebar';
import { CatalogoService, Exemplar, Livro, Pagina } from './catalogo.service';

type Secao = 'livros' | 'exemplares';
type ExemplarListado = Exemplar & { tituloLivro: string };
type Lista =
  | { secao: 'livros'; pagina: Pagina<Livro> }
  | {
      secao: 'exemplares';
      pagina: Pagina<ExemplarListado>;
    };

function erroCatalogo(error: unknown): string {
  if (error instanceof HttpErrorResponse && [404, 409].includes(error.status)) {
    return (
      (error.error as ProblemaApi | null)?.detail ||
      (error.status === 404
        ? 'Registro do catálogo não encontrado.'
        : 'A alteração gerou um conflito.')
    );
  }
  return mensagemErro(error);
}

@Component({
  selector: 'app-catalogo',
  imports: [ReactiveFormsModule, Sidebar],
  templateUrl: './catalogo.html',
  styleUrl: './catalogo.css',
})
export class Catalogo {
  private readonly api = inject(CatalogoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly forms = inject(FormBuilder).nonNullable;
  @ViewChild('dialogo', { static: true }) private dialogo!: ElementRef<HTMLDialogElement>;

  protected readonly secao = signal<Secao>('livros');
  protected readonly lista = signal<Lista | null>(null);
  private readonly paginaSolicitada = signal(0);
  protected readonly tamanho = signal(10);
  protected readonly carregando = signal(false);
  protected readonly erroLista = signal('');
  protected readonly sucesso = signal('');
  protected readonly editando = signal<string | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroForm = signal('');
  protected readonly errosCampos = signal<Record<string, string>>({});
  protected readonly formLivro = this.forms.group({ titulo: '', autor: '', isbn: '' });
  protected readonly formExemplar = this.forms.group({ codigo: '' });
  protected readonly livroSelecionado = signal<{ id: string; titulo: string } | null>(null);
  protected readonly escolhendoLivro = signal(false);
  protected readonly opcoes = signal<Pagina<Livro> | null>(null);
  protected readonly carregandoLivros = signal(false);
  protected readonly erroLivros = signal('');
  protected readonly paginaLivros = signal(0);

  constructor() {
    this.carregar();
  }

  protected mudarSecao(secao: Secao) {
    if (this.carregando() || this.secao() === secao) return;
    this.secao.set(secao);
    this.lista.set(null);
    this.sucesso.set('');
    this.carregar(0);
  }

  protected mudarTamanho(tamanho: string) {
    this.tamanho.set(Number(tamanho));
    this.carregar(0);
  }

  protected carregar(page = this.paginaSolicitada()) {
    if (this.carregando()) return;
    this.paginaSolicitada.set(page);
    this.carregando.set(true);
    this.erroLista.set('');
    const request =
      this.secao() === 'livros'
        ? this.api
            .livros(page, this.tamanho())
            .pipe(map((pagina): Lista => ({ secao: 'livros', pagina })))
        : this.api.exemplares(page, this.tamanho()).pipe(
            switchMap((pagina) => {
              const ids = [...new Set(pagina.items.map(({ livroId }) => livroId))];
              const titulos = ids.map((id) =>
                this.api.livro(id).pipe(
                  map(({ titulo }) => [id, titulo] as const),
                  catchError((error: unknown) => {
                    if (!(error instanceof HttpErrorResponse) || error.status !== 404) throw error;
                    return of([id, 'Livro não encontrado'] as const);
                  }),
                ),
              );
              return (titulos.length ? forkJoin(titulos) : of([])).pipe(
                map((pares): Lista => {
                  const porId = new Map(pares);
                  const items = pagina.items.map((item) => ({
                    ...item,
                    tituloLivro: porId.get(item.livroId) ?? 'Livro não encontrado',
                  }));
                  return { secao: 'exemplares', pagina: { ...pagina, items } };
                }),
              );
            }),
          );
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (lista) => this.lista.set(lista),
        error: (error: unknown) => this.erroLista.set(erroCatalogo(error)),
      });
  }

  protected abrir(item?: Livro | ExemplarListado) {
    this.editando.set(item?.id ?? null);
    this.limparErros();
    this.opcoes.set(null);
    this.erroLivros.set('');
    this.escolhendoLivro.set(false);
    const livro = item && 'titulo' in item ? item : undefined;
    const exemplar = item && 'codigo' in item ? item : undefined;
    this.formLivro.reset({
      titulo: livro?.titulo ?? '',
      autor: livro?.autor ?? '',
      isbn: livro?.isbn ?? '',
    });
    this.formExemplar.reset({ codigo: exemplar?.codigo ?? '' });
    this.livroSelecionado.set(
      exemplar ? { id: exemplar.livroId, titulo: exemplar.tituloLivro } : null,
    );
    this.dialogo.nativeElement.showModal();
    this.dialogo.nativeElement
      .querySelector<HTMLInputElement>(this.secao() === 'livros' ? '#titulo' : '#codigo')
      ?.focus();
  }

  protected fechar(event?: Event) {
    if (this.salvando() || this.carregandoLivros()) {
      event?.preventDefault();
      return;
    }
    this.dialogo.nativeElement.close();
  }

  protected alternarLivros() {
    if (this.escolhendoLivro()) {
      this.escolhendoLivro.set(false);
    } else {
      this.carregarLivros();
    }
  }

  protected carregarLivros(page = 0) {
    this.escolhendoLivro.set(true);
    this.paginaLivros.set(page);
    this.carregandoLivros.set(true);
    this.erroLivros.set('');
    this.api
      .livros(page, 10)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregandoLivros.set(false)),
      )
      .subscribe({
        next: (pagina) => this.opcoes.set(pagina),
        error: (error: unknown) => this.erroLivros.set(erroCatalogo(error)),
      });
  }

  protected selecionarLivro(livro: Livro) {
    this.livroSelecionado.set(livro);
    this.escolhendoLivro.set(false);
    this.limparErros();
    this.dialogo.nativeElement.querySelector<HTMLButtonElement>('#escolher-livro')?.focus();
  }

  protected limparErros() {
    this.erroForm.set('');
    this.errosCampos.set({});
  }

  protected salvar() {
    if (this.salvando() || this.carregandoLivros()) return;
    this.limparErros();
    const id = this.editando();
    const livro = this.formLivro.getRawValue();
    const titulo = livro.titulo.trim();
    const autor = livro.autor.trim();
    const isbn = livro.isbn.trim() || null;
    const codigo = this.formExemplar.getRawValue().codigo.trim();
    const livroId = this.livroSelecionado()?.id ?? '';
    const erros: Record<string, string> = {};
    const salvandoLivro = this.secao() === 'livros';
    if (salvandoLivro) {
      if (!titulo) erros['titulo'] = 'Informe o título.';
      if (!autor) erros['autor'] = 'Informe o autor.';
    } else {
      if (!codigo) erros['codigo'] = 'Informe o código.';
      if (!livroId) erros['livroId'] = 'Selecione um livro.';
    }
    if (Object.keys(erros).length) {
      this.errosCampos.set(erros);
      this.erroForm.set('Preencha os campos obrigatórios.');
      return;
    }
    const request: Observable<Livro | Exemplar> = salvandoLivro
      ? this.api.salvarLivro(id, { titulo, autor, isbn })
      : this.api.salvarExemplar(id, { codigo, livroId });
    this.salvando.set(true);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.salvando.set(false)),
      )
      .subscribe({
        next: () => {
          this.dialogo.nativeElement.close();
          this.sucesso.set(id ? 'Alteração salva.' : 'Cadastro concluído.');
          this.carregar(id ? this.paginaSolicitada() : 0);
        },
        error: (error: unknown) => {
          this.erroForm.set(erroCatalogo(error));
          if (error instanceof HttpErrorResponse && error.status === 400) {
            this.errosCampos.set((error.error as ProblemaApi | null)?.errors ?? {});
          }
        },
      });
  }
}
