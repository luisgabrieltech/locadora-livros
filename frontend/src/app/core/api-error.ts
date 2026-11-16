import { HttpErrorResponse } from '@angular/common/http';

export interface ProblemaApi {
  errors?: Record<string, string>;
}

export function mensagemErro(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    switch (error.status) {
      case 400:
        return 'Revise os campos informados e tente novamente.';
      case 401:
        return 'Usuário ou senha inválidos.';
      case 403:
        return 'Não foi possível validar a operação. Saia e entre novamente.';
      case 404:
        return 'O cliente não foi encontrado. Atualize a lista e tente novamente.';
      case 409:
        return 'Já existe um cliente com este CPF.';
      case 0:
        return 'Não foi possível conectar à API. Confira se ela está em execução.';
      default:
        return 'Não foi possível concluir a operação. Tente novamente.';
    }
  }
  return 'Não foi possível conectar à API no tempo esperado. Tente novamente.';
}
