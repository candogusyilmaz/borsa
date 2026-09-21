package dev.canverse.stocks.platform.error;

import dev.canverse.stocks.platform.web.trace.RequestTraceFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.OptimisticLockException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_BASE = "https://canverse.dev/problems/";

    private final Clock clock;
    private final Tracer tracer;

    @Autowired
    public GlobalExceptionHandler(Clock clock, Tracer tracer) {
        this.clock = clock;
        this.tracer = tracer;
    }

    /**
     * Test-only compatibility constructor for callers that do not need native tracing.
     */
    public GlobalExceptionHandler(Clock clock) {
        this(clock, Tracer.NOOP);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Object> handleAppException(AppException exception, WebRequest request) {
        var errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Application error code={} traceId={} params={}", errorCode.getCode(), traceId(request), exception.getParams(), exception);
        } else {
            log.warn("Application error code={} traceId={} params={}", errorCode.getCode(), traceId(request), exception.getParams());
        }

        if (errorCode == CommonErrorCode.VALIDATION_FAILED && exception.getParams().containsKey("errors")) {
            var rawErrors = exception.getParams().get("errors");
            if (rawErrors instanceof List<?> list) {
                var validationErrors = new ArrayList<ValidationError>();
                for (var item : list) {
                    if (item instanceof ValidationError ve) {
                        validationErrors.add(ve);
                    } else if (item instanceof Map<?, ?> map) {
                        var field = java.util.Objects.toString(map.get("field"), "");
                        var key = java.util.Objects.toString(map.get("key"), ValidationKeySupport.FALLBACK_KEY);
                        var detail = java.util.Objects.toString(map.get("detail"), "Validation failed.");
                        @SuppressWarnings("unchecked")
                        var p = map.get("params") instanceof Map<?, ?> mp ? (Map<String, Object>) mp : null;
                        validationErrors.add(new ValidationError(field, key, detail, p));
                    }
                }
                return validationProblemResponse(errorCode, validationErrors, HttpHeaders.EMPTY, request);
            }
        }

        return problemResponse(errorCode, exception.getParams(), HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException exception, WebRequest request) {
        var mappedErrorCode = DatabaseConstraintRegistry.resolve(exception);
        if (mappedErrorCode.isPresent()) {
            return handleAppException(new AppException(mappedErrorCode.get(), exception), request);
        }
        return unknownPersistenceFailure(exception, request);
    }

    @ExceptionHandler(org.hibernate.exception.ConstraintViolationException.class)
    public ResponseEntity<Object> handleHibernateConstraintViolation(org.hibernate.exception.ConstraintViolationException exception, WebRequest request) {
        var mappedErrorCode = DatabaseConstraintRegistry.resolve(exception);
        if (mappedErrorCode.isPresent()) {
            return handleAppException(new AppException(mappedErrorCode.get(), exception), request);
        }
        return unknownPersistenceFailure(exception, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        var errors = exception.getConstraintViolations().stream().map(this::validationError).toList();
        return validationResponse(errors, HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Object> handleOptimisticLockConflict(Exception exception, WebRequest request) {
        log.warn("Optimistic locking conflict traceId={} nativeTraceId={}", traceId(request), nativeTraceId(), exception);
        return problemResponse(CommonErrorCode.STATE_CONFLICT, Map.of(), HttpHeaders.EMPTY, request);
    }

    private ResponseEntity<Object> unknownPersistenceFailure(Throwable exception, WebRequest request) {
        log.error("Unknown persistence failure traceId={} nativeTraceId={}", traceId(request), nativeTraceId(), exception);
        return problemResponse(CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "Unhandled persistence failure"), HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unhandled exception traceId={} nativeTraceId={}", traceId(request), nativeTraceId(), exception);
        return problemResponse(CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "Unhandled server exception"), HttpHeaders.EMPTY, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.METHOD_NOT_ALLOWED, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.NOT_ACCEPTABLE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(MissingPathVariableException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return problemResponse(CommonErrorCode.MISSING_REQUEST_VALUE, Map.of("parameter", exception.getParameterName()), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return problemResponse(CommonErrorCode.MISSING_REQUEST_VALUE, Map.of("parameter", exception.getRequestPartName()), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.REQUEST_BINDING_FAILED, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        var errors = exception.getBindingResult().getAllErrors().stream().map(this::validationError).toList();
        return validationResponse(errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return validationResponse(validationErrors(exception), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.RESOURCE_NOT_FOUND, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.RESOURCE_NOT_FOUND, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.SERVICE_UNAVAILABLE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.PAYLOAD_TOO_LARGE, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleConversionNotSupported(ConversionNotSupportedException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return frameworkResponse(CommonErrorCode.MALFORMED_REQUEST, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return frameworkResponse(CommonErrorCode.MALFORMED_REQUEST, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        return internalFrameworkResponse(headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodValidationException(MethodValidationException exception, HttpHeaders headers, HttpStatus status,
            WebRequest request) {
        return validationResponse(validationErrors(exception), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException exception, WebRequest request) {
        return internalFrameworkResponse(HttpHeaders.EMPTY, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        var errorCode = frameworkErrorCode(status);
        if (errorCode == CommonErrorCode.INTERNAL_ERROR) {
            return internalFrameworkResponse(headers, request, exception);
        }
        if (errorCode == CommonErrorCode.VALIDATION_FAILED) {
            return validationResponse(List.of(), headers, request);
        }
        return frameworkResponse(errorCode, headers, request, exception);
    }

    private ResponseEntity<Object> validationResponse(List<ValidationError> errors, HttpHeaders headers, WebRequest request) {
        var safeErrors = errors.isEmpty() ? List.of(new ValidationError("", ValidationKeySupport.FALLBACK_KEY, "Validation failed.", null))
                : List.copyOf(errors);
        log.warn("Request validation failed traceId={} nativeTraceId={} errorCount={}", traceId(request), nativeTraceId(), safeErrors.size());
        return validationProblemResponse(CommonErrorCode.VALIDATION_FAILED, safeErrors, headers, request);
    }

    private List<ValidationError> validationErrors(MethodValidationResult exception) {
        var errors = new ArrayList<ValidationError>();
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

    private ValidationError validationError(ConstraintViolation<?> violation) {
        var field = lastPathSegment(violation.getPropertyPath().toString());
        var constraintName = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return validationEntry(field, constraintName, violation.getMessageTemplate(), safeAttributes(violation));
    }

    private ValidationError validationError(ObjectError error) {
        var violation = unwrapViolation(error);
        var field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        var constraintName = violation == null ? constraintName(error.getCodes())
                : violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        var messageTemplate = violation == null ? error.getDefaultMessage() : violation.getMessageTemplate();
        Map<String, Object> attributes = violation == null ? Map.of() : safeAttributes(violation);
        return validationEntry(field, constraintName, messageTemplate, attributes);
    }

    private ValidationError validationError(String field, MessageSourceResolvable error) {
        return validationEntry(field, constraintName(error.getCodes()), error.getDefaultMessage(), Map.of());
    }

    private ValidationError validationEntry(String field, String constraintName, String messageTemplate, Map<String, Object> attributes) {
        var explicitKey = ValidationKeySupport.explicitApplicationKey(messageTemplate);
        var builtInKey = ValidationKeySupport.builtInKey(constraintName);
        if (explicitKey == null && builtInKey == null) {
            log.warn("Unmapped validation message template constraint={} template={}", constraintName, messageTemplate);
        }
        var key = explicitKey == null ? builtInKey == null ? ValidationKeySupport.FALLBACK_KEY : builtInKey : explicitKey;
        var detail = ValidationKeySupport.safeDetail(constraintName, messageTemplate, explicitKey != null);
        return new ValidationError(field == null ? "" : field, key, detail, attributes.isEmpty() ? null : attributes);
    }

    private ResponseEntity<Object> frameworkResponse(ErrorCode errorCode, HttpHeaders headers, WebRequest request, Exception exception) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Framework error mapped to {} traceId={} nativeTraceId={}", errorCode.getCode(), traceId(request), nativeTraceId(), exception);
        }
        return problemResponse(errorCode, Map.of(), headers, request);
    }

    private ResponseEntity<Object> internalFrameworkResponse(HttpHeaders headers, WebRequest request, Exception exception) {
        log.error("Internal framework error traceId={} nativeTraceId={}", traceId(request), nativeTraceId(), exception);
        return problemResponse(CommonErrorCode.INTERNAL_ERROR, Map.of("detail", "Internal framework failure"), headers, request);
    }

    private ResponseEntity<Object> validationProblemResponse(ErrorCode errorCode, List<ValidationError> errors, HttpHeaders headers, WebRequest request) {
        var type = URI.create(PROBLEM_TYPE_BASE + toKebabCase(errorCode.getCode()));
        var title = reasonPhrase(errorCode.getStatus());
        var instance = requestUri(request);
        var code = errorCode.getCode();
        var key = errorCode.getMessageKey();
        var trace = traceId(request);
        var timestamp = Instant.now(clock);
        var validationProblem = new ValidationProblem(type, title, errorCode.getStatus().value(), null, instance, code, key, trace, timestamp,
                new ValidationProblem.ValidationParams(errors));

        var responseHeaders = new HttpHeaders();
        if (headers != null) {
            responseHeaders.putAll(headers);
        }
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(validationProblem, responseHeaders, errorCode.getStatus());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> problemResponse(ErrorCode errorCode, Map<String, ?> params, HttpHeaders headers, WebRequest request) {
        var type = URI.create(PROBLEM_TYPE_BASE + toKebabCase(errorCode.getCode()));
        var title = reasonPhrase(errorCode.getStatus());
        var instance = requestUri(request);
        var code = errorCode.getCode();
        var key = errorCode.getMessageKey();
        var trace = traceId(request);
        var timestamp = Instant.now(clock);

        Map<String, Object> safeParams = null;
        if (errorCode.getStatus().is4xxClientError() && params != null && !params.isEmpty()) {
            safeParams = (Map<String, Object>) params;
        }

        var apiProblem = new ApiProblem(type, title, errorCode.getStatus().value(), null, instance, code, key, trace, timestamp, safeParams);

        var responseHeaders = new HttpHeaders();
        if (headers != null) {
            responseHeaders.putAll(headers);
        }
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(apiProblem, responseHeaders, errorCode.getStatus());
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
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character;
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

    private String nativeTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan == null ? "none" : currentSpan.context().traceId();
    }
}
