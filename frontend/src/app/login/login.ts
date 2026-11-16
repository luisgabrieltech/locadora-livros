import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { mensagemErro } from '../core/api-error';

@Component({ selector: 'app-login', imports: [ReactiveFormsModule], templateUrl: './login.html' })
export class Login {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly enviando = signal(false);
  protected readonly erro = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    usuario: ['', [Validators.required, Validators.pattern(/\S/)]],
    senha: ['', [Validators.required]],
  });

  constructor() {
    if (this.auth.autenticado()) void this.router.navigateByUrl('/clientes');
  }

  protected entrar() {
    if (this.enviando()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.form.controls.usuario.value.trim()) return;
    const { usuario, senha } = this.form.getRawValue();
    this.enviando.set(true);
    this.erro.set('');
    this.auth
      .entrar(usuario.trim(), senha)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.enviando.set(false);
          this.form.controls.senha.reset('');
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/clientes');
        },
        error: (error: unknown) => {
          this.erro.set(mensagemErro(error));
        },
      });
  }
}
