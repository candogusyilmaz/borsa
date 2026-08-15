package dev.canverse.stocks.platform.error;

import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_BASE = "https://canverse.dev/problems/";

    private final Clock clock;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Object> handleAppException(AppException exception, WebRequest request) {
        var errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error(
                    "Application error code={} traceId={} params={}",
                    errorCode.getCode(),
                    traceId(request),
                    exception.getParams(),
                    exception);
        } else {
            log.warn(
                    "Application error code={} traceId={} params={}",
                    errorCode.getCode(),
                    traceId(request),
                    exception.getParams());
        }
        return problemResponse(errorCode, exception.getParams(), HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception, WebRequest request) {
        var errors = exception.getConstraintViolations().stream()
                .map(this::validationError)
                .toList();
        return validationResponse(errors, HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Object> handlePersistenceConflict(Exception exception, WebRequest request) {
        log.warn("Persistence conflict traceId={}", traceId(request), exception);
        return problemResponse(CommonErrorCode.STATE_CONFLICT, Map.of(), HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unhandled exception traceId={}", traceId(request), exception);
        return problemResponse(
                CommonErrorCode.INTERNAL_ERROR,
                Map.of("detail", "Unhandled server exception"),
                HttpHeaders.EMPTY,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.METHOD_NOT_ALLOWED, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.NOT_ACCEPTABLE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(
            MissingPathVariableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problemResponse(
                CommonErrorCode.MISSING_REQUEST_VALUE,
                Map.of("parameter", exception.getParameterName()),
                headers,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problemResponse(
                CommonErrorCode.MISSING_REQUEST_VALUE,
                Map.of("parameter", exception.getRequestPartName()),
                headers,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.REQUEST_BINDING_FAILED, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = exception.getBindingResult().getAllErrors().stream()
                .map(this::validationError)
                .toList();
        return validationResponse(errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationResponse(validationErrors(exception), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.RESOURCE_NOT_FOUND, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.RESOURCE_NOT_FOUND, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestTimeoutException(
            AsyncRequestTimeoutException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.SERVICE_UNAVAILABLE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.PAYLOAD_TOO_LARGE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleConversionNotSupported(
            ConversionNotSupportedException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.MALFORMED_REQUEST, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.MALFORMED_REQUEST, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(
            HttpMessageNotWritableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodValidationException(
            MethodValidationException exception, HttpHeaders headers, HttpStatus status, WebRequest request) {
        return validationResponse(validationErrors(exception), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException exception, WebRequest request) {
        return internalFrameworkResponse(HttpHeaders.EMPTY, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
            ErrorResponseException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errorCode = frameworkErrorCode(status);
        if (errorCode == CommonErrorCode.INTERNAL_ERROR) {
            return internalFrameworkResponse(headers, request, exception);
        }
        if (errorCode == CommonErrorCode.VALIDATION_FAILED) {
            return validationResponse(List.of(), headers, request);
        }
        return frameworkResponse(errorCode, headers, request, exception);
    }

    private ResponseEntity<Object> validationResponse(
            List<Map<String, Object>> errors, HttpHeaders headers, WebRequest request) {
        var safeErrors = errors.isEmpty()
                ? List.of(Map.of(
                        "field", "",
                        "key", ValidationKeySupport.FALLBACK_KEY,
                        "detail", "Validation failed."))
                : List.copyOf(errors);
        log.warn("Request validation failed traceId={} errorCount={}", traceId(request), safeErrors.size());
        return problemResponse(CommonErrorCode.VALIDATION_FAILED, Map.of("errors", safeErrors), headers, request);
    }

    private List<Map<String, Object>> validationErrors(MethodValidationResult exception) {
        var errors = new ArrayList<Map<String, Object>>();
        for (var result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                for (var error : parameterErrors.getAllErrors()) {
                    errors.add(validationError(error));
                }
            } else {
                var field = parameterName(result);
                for (var error : result.getResolvableErrors()) {
                    errors.add(validationError(field, error));
                }
            }
        }
        for (var error : exception.getCrossParameterValidationResults()) {
            errors.add(validationError("parameters", error));
        }
        return List.copyOf(errors);
    }

    private Map<String, Object> validationError(ConstraintViolation<?> violation) {
        var field = lastPathSegment(violation.getPropertyPath().toString());
        var constraintName = violation
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
        return validationEntry(field, constraintName, violation.getMessageTemplate(), safeAttributes(violation));
    }

    private Map<String, Object> validationError(ObjectError error) {
        var violation = unwrapViolation(error);
        var field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        var constraintName = violation == null
                ? constraintName(error.getCodes())
                : violation
                        .getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .getSimpleName();
        var messageTemplate = violation == null ? error.getDefaultMessage() : violation.getMessageTemplate();
        Map<String, Object> attributes = violation == null ? Map.of() : safeAttributes(violation);
        return validationEntry(field, constraintName, messageTemplate, attributes);
    }

    private Map<String, Object> validationError(String field, MessageSourceResolvable error) {
        return validationEntry(field, constraintName(error.getCodes()), error.getDefaultMessage(), Map.of());
    }

    private Map<String, Object> validationEntry(
            String field, String constraintName, String messageTemplate, Map<String, Object> attributes) {
        var explicitKey = ValidationKeySupport.explicitApplicationKey(messageTemplate);
        var builtInKey = ValidationKeySupport.builtInKey(constraintName);
        if (explicitKey == null && builtInKey == null) {
            log.warn("Unmapped validation message template constraint={} template={}", constraintName, messageTemplate);
        }
        var key =
                explicitKey == null ? builtInKey == null ? ValidationKeySupport.FALLBACK_KEY : builtInKey : explicitKey;
        var detail = ValidationKeySupport.safeDetail(constraintName, messageTemplate, explicitKey != null);
        var error = new LinkedHashMap<String, Object>();
        error.put("field", field == null ? "" : field);
        error.put("key", key);
        error.put("detail", detail);
        if (!attributes.isEmpty()) {
            error.put("params", attributes);
        }
        return Map.copyOf(error);
    }

    private ResponseEntity<Object> frameworkResponse(
            ErrorCode errorCode, HttpHeaders headers, WebRequest request, Exception exception) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Framework error mapped to {} traceId={}", errorCode.getCode(), traceId(request), exception);
        }
        return problemResponse(errorCode, Map.of(), headers, request);
    }

    private ResponseEntity<Object> internalFrameworkResponse(
            HttpHeaders headers, WebRequest request, Exception exception) {
        log.error("Internal framework error traceId={}", traceId(request), exception);
        return problemResponse(
                CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "Internal framework failure"), headers, request);
    }

    private ResponseEntity<Object> problemResponse(
            ErrorCode errorCode, Map<String, ?> params, HttpHeaders headers, WebRequest request) {
        var problem = ProblemDetail.forStatus(errorCode.getStatus());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + toKebabCase(errorCode.getCode())));
        problem.setTitle(reasonPhrase(errorCode.getStatus()));
        problem.setInstance(requestUri(request));
        problem.setProperty("code", errorCode.getCode());
        problem.setProperty("key", errorCode.getMessageKey());
        problem.setProperty("traceId", traceId(request));
        problem.setProperty("timestamp", Instant.now(clock));
        if (errorCode.getStatus().is4xxClientError() && params != null && !params.isEmpty()) {
            problem.setProperty("params", params);
        }

        var responseHeaders = new HttpHeaders();
        if (headers != null) {
            responseHeaders.putAll(headers);
        }
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, errorCode.getStatus());
    }

    private static String constraintName(String[] codes) {
        if (codes == null) {
            return null;
        }
        for (var code : codes) {
            if (code == null) {
                continue;
            }
            var candidate = code.substring(0, code.indexOf('.') < 0 ? code.length() : code.indexOf('.'));
            if (ValidationKeySupport.builtInKey(candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    private static ConstraintViolation<?> unwrapViolation(ObjectError error) {
        if (!error.contains(ConstraintViolation.class)) {
            return null;
        }
        return error.unwrap(ConstraintViolation.class);
    }

    private static Map<String, Object> safeAttributes(ConstraintViolation<?> violation) {
        var safe = new LinkedHashMap<String, Object>();
        var attributes = violation.getConstraintDescriptor().getAttributes();
        var allowed = Set.of("min", "max", "value", "inclusive", "regexp", "integer", "fraction");
        for (var entry : attributes.entrySet()) {
            if (allowed.contains(entry.getKey()) && isSafeAttribute(entry.getValue())) {
                safe.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(safe);
    }

    private static boolean isSafeAttribute(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }

    private static String parameterName(ParameterValidationResult result) {
        var parameter = result.getMethodParameter();
        var requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && hasText(requestParam.name())) {
            return requestParam.name();
        }
        var pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && hasText(pathVariable.name())) {
            return pathVariable.name();
        }
        var requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null && hasText(requestHeader.name())) {
            return requestHeader.name();
        }
        var requestPart = parameter.getParameterAnnotation(RequestPart.class);
        if (requestPart != null && hasText(requestPart.name())) {
            return requestPart.name();
        }
        var cookie = parameter.getParameterAnnotation(CookieValue.class);
        if (cookie != null && hasText(cookie.name())) {
            return cookie.name();
        }
        var matrix = parameter.getParameterAnnotation(MatrixVariable.class);
        if (matrix != null && hasText(matrix.name())) {
            return matrix.name();
        }
        return parameter.getParameterName() == null ? "parameter" : parameter.getParameterName();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String lastPathSegment(String path) {
        var separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static ErrorCode frameworkErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> CommonErrorCode.MALFORMED_REQUEST;
            case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
            case 409 -> CommonErrorCode.STATE_CONFLICT;
            case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> CommonErrorCode.VALIDATION_FAILED;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            default -> CommonErrorCode.INTERNAL_ERROR;
        };
    }

    private static String reasonPhrase(HttpStatusCode status) {
        var resolved = HttpStatus.resolve(status.value());
        return resolved == null ? status.toString() : resolved.getReasonPhrase();
    }

    private static String toKebabCase(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static URI requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return URI.create(servletWebRequest.getRequest().getRequestURI());
        }
        var description = request.getDescription(false);
        return URI.create(description.startsWith("uri=") ? description.substring("uri=".length()) : "/");
    }

    private static String traceId(WebRequest request) {
        var traceId = request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        return traceId == null ? "unknown" : traceId.toString();
    }
}
