package br.com.locadora.system;

import br.com.locadora.catalogo.LivroNaoEncontradoException;
import br.com.locadora.catalogo.ExemplarNaoEncontradoException;
import br.com.locadora.catalogo.CodigoExemplarDuplicadoException;
import br.com.locadora.catalogo.ExemplarComHistoricoException;
import br.com.locadora.locacoes.LocacaoInvalidaException;
import br.com.locadora.locacoes.LocacaoConflitoException;
import br.com.locadora.locacoes.LocacaoNaoEncontradaException;
import br.com.locadora.locacoes.ItemLocacaoNaoEncontradoException;
import br.com.locadora.clientes.ClienteNaoEncontradoException;
import br.com.locadora.clientes.CpfDuplicadoException;
import br.com.locadora.clientes.PaginacaoInvalidaException;
import java.util.Map;
import java.util.TreeMap;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LocacaoInvalidaException.class)
    ProblemDetail locacaoInvalida(LocacaoInvalidaException exception) {
        return problema(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({LocacaoNaoEncontradaException.class, ItemLocacaoNaoEncontradoException.class})
    ProblemDetail locacaoNaoEncontrada(RuntimeException exception) {
        return problema(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({LocacaoConflitoException.class, ExemplarComHistoricoException.class})
    ProblemDetail conflitoLocacao(RuntimeException exception) {
        return problema(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({LivroNaoEncontradoException.class, ExemplarNaoEncontradoException.class})
    ProblemDetail catalogoNaoEncontrado(RuntimeException exception) {
        return problema(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CodigoExemplarDuplicadoException.class)
    ProblemDetail codigoExemplarDuplicado(CodigoExemplarDuplicadoException exception) {
        return problema(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PaginacaoInvalidaException.class)
    ProblemDetail paginacaoInvalida(PaginacaoInvalidaException exception) {
        return problema(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ClienteNaoEncontradoException.class)
    ProblemDetail naoEncontrado(ClienteNaoEncontradoException exception) {
        return problema(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CpfDuplicadoException.class)
    ProblemDetail cpfDuplicado(CpfDuplicadoException exception) {
        return problema(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail parametroInvalido(IllegalArgumentException exception) {
        return problema(HttpStatus.BAD_REQUEST, "Parâmetros inválidos: página não negativa e tamanho entre 1 e 100.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uk_itens_exemplar_ativo".equals(violation.getConstraintName())) {
                return problema(HttpStatus.CONFLICT, "Um ou mais exemplares já estão emprestados.");
            }
            if (cause instanceof ConstraintViolationException violation
                    && "uk_exemplares_codigo".equals(violation.getConstraintName())) {
                return problema(HttpStatus.CONFLICT, "Já existe um exemplar com o código informado.");
            }
            if (cause instanceof ConstraintViolationException violation
                    && "uk_clientes_cpf".equals(violation.getConstraintName())) {
                return problema(HttpStatus.CONFLICT, "Já existe um cliente com o CPF informado.");
            }
        }
        // Não registrar a exceção SQL, pois pode conter dados pessoais.
        LOG.error("Falha inesperada de integridade ao persistir dados.");
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var body = problema(HttpStatus.BAD_REQUEST, "Verifique os campos informados.");
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Evita mensagens de conversão que incluam valores rejeitados ou detalhes internos.
        var safeBody = body instanceof ProblemDetail problem && exception instanceof MethodArgumentNotValidException
                ? problem : ProblemDetail.forStatusAndDetail(status,
                        status.is5xxServerError() ? "Não foi possível concluir a operação." : "Requisição inválida.");
        return super.handleExceptionInternal(exception, safeBody, headers, status, request);
    }

    private ProblemDetail problema(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
