import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { Sidebar } from '../sidebar';
import { mensagemErro, ProblemaApi } from '../core/api-error';
import { ClientesService, PaginaClientes } from './clientes.service';

type Campo = 'nome' | 'telefone' | 'cpf' | 'email';

@Component({
  selector: 'app-clientes',
  imports: [ReactiveFormsModule, Sidebar],
  templateUrl: './clientes.html',
})
export class Clientes {
  private readonly api = inject(ClientesService);
  private readonly destroyRef = inject(DestroyRef);
  @ViewChild('dialogo', { static: true }) private dialogo!: ElementRef<HTMLDialogElement>;
  protected readonly dados = signal<PaginaClientes>({
    items: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
  });
  protected readonly tamanho = signal(10);
  protected readonly carregando = signal(false);
  protected readonly lendo = signal(false);
  protected readonly salvando = signal(false);
  protected readonly editando = signal<string | null>(null);
  protected readonly erroLista = signal('');
  protected readonly erroForm = signal('');
  protected readonly sucesso = signal('');
  protected readonly errosCampos = signal<Record<string, string>>({});
  protected readonly form = inject(FormBuilder).nonNullable.group({
    nome: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    telefone: ['', [Validators.required, Validators.pattern(/^[+\d()\s-]{10,30}$/)]],
    cpf: ['', [Validators.required, Validators.pattern(/^(\d{11}|\d{3}\.\d{3}\.\d{3}-\d{2})$/)]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.errosCampos.set({});
      this.erroForm.set('');
    });
    this.carregar();
  }

  protected carregar(page = this.dados().page) {
    if (this.carregando()) return;
    this.carregando.set(true);
    this.erroLista.set('');
    this.api
      .listar(page, this.tamanho())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.carregando.set(false)),
      )
      .subscribe({
        next: (pagina) => this.dados.set(pagina),
        error: (error: unknown) => this.erroLista.set(mensagemErro(error)),
      });
  }

  protected mudarTamanho(valor: string) {
    this.tamanho.set(Number(valor));
    this.carregar(0);
  }

  protected novo() {
    if (this.lendo() || this.carregando()) return;
    this.editando.set(null);
    this.form.reset();
    this.erroForm.set('');
    this.errosCampos.set({});
    this.abrirFormulario();
  }

  protected editar(id: string) {
    if (this.lendo()) return;
    this.lendo.set(true);
    this.erroLista.set('');
    this.api
      .buscar(id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.lendo.set(false)),
      )
      .subscribe({
        next: (cliente) => {
          this.editando.set(id);
          this.form.reset({
            nome: cliente.nome,
            telefone: cliente.telefone,
            cpf: cliente.cpf,
            email: cliente.email ?? '',
          });
          this.erroForm.set('');
          this.errosCampos.set({});
          this.abrirFormulario();
        },
        error: (error: unknown) => this.erroLista.set(mensagemErro(error)),
      });
  }

  private abrirFormulario() {
    this.dialogo.nativeElement.showModal();
    this.dialogo.nativeElement.querySelector<HTMLInputElement>('#nome')?.focus();
  }

  protected fechar(event?: Event) {
    if (this.salvando()) {
      event?.preventDefault();
      return;
    }
    this.dialogo.nativeElement.close();
    this.form.reset();
    this.erroForm.set('');
    this.errosCampos.set({});
  }

  protected salvar() {
    if (this.salvando()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const valores = this.form.getRawValue();
    const dados = { ...valores, nome: valores.nome.trim(), email: valores.email.trim() || null };
    const id = this.editando();
    this.salvando.set(true);
    this.erroForm.set('');
    this.errosCampos.set({});
    const request = id ? this.api.atualizar(id, dados) : this.api.criar(dados);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.salvando.set(false)),
      )
      .subscribe({
        next: () => {
          this.dialogo.nativeElement.close();
          this.form.reset();
          this.sucesso.set(
            id ? 'Dados do cliente atualizados.' : 'Cliente cadastrado com sucesso.',
          );
          this.carregar(id ? this.dados().page : 0);
        },
        error: (error: unknown) => {
          this.erroForm.set(mensagemErro(error));
          if (error instanceof HttpErrorResponse && error.status === 400) {
            const problema = error.error as ProblemaApi;
            this.errosCampos.set(problema?.errors ?? {});
          }
        },
      });
  }

  protected erroCampo(campo: Campo): string {
    const servidor = this.errosCampos()[campo];
    if (servidor) return servidor;
    const control = this.form.controls[campo];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('required')) return 'Campo obrigatório.';
    if (control.hasError('minlength')) return 'Informe ao menos 2 caracteres.';
    if (control.hasError('maxlength')) return 'O texto excede o tamanho permitido.';
    if (campo === 'cpf') return 'Informe 11 dígitos ou use o formato 000.000.000-00.';
    if (campo === 'telefone') return 'Informe um telefone com DDD.';
    return 'Informe um e-mail válido.';
  }

  protected iniciais(nome: string) {
    return nome
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toLocaleUpperCase('pt-BR');
  }
}
