import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError, timeout } from 'rxjs';
import { AuthService } from './auth.service';

export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  // Nunca enviar Basic ou CSRF a recursos externos.
  if (!req.url.startsWith('/api/v1/')) return next(req);
  const auth = inject(AuthService);
  const headers = auth.headers(req.method);
  let authenticated = req;
  for (const name of headers.keys()) {
    authenticated = authenticated.clone({
      headers: authenticated.headers.set(name, headers.get(name)!),
    });
  }
  return next(authenticated).pipe(
    timeout(15000),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.sair('Entre novamente para continuar.');
      }
      return throwError(() => error);
    }),
  );
};
