package com.example.raisetime.exception;

import com.example.raisetime.dto.ErrorResponse;
import com.example.raisetime.dto.ErrorResponse.FieldErrorResponse;
import com.example.raisetime.logging.LogContext;
import com.example.raisetime.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// コントローラーで起きた例外を、共通仕様（docs/機能定義書/00_共通仕様.md 3章）の形のエラーレスポンスにする
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String VALIDATION_MESSAGE = "入力内容に誤りがあります";
    static final String LOGIN_FAILED_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // 1つの項目に複数のエラーがあっても、最初の1つだけ返す
        Map<String, String> firstErrorByField = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            firstErrorByField.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        List<FieldErrorResponse> errors = firstErrorByField.entrySet().stream()
                .map(entry -> new FieldErrorResponse(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", VALIDATION_MESSAGE, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "リクエストの形式が正しくありません"));
    }

    // 本文の文字数、画像の枚数・形式など
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(),
                List.of(new FieldErrorResponse(ex.getField(), ex.getMessage()))));
    }

    // 必須のパラメーター・ファイルが無い、パラメーターの型が違う（before=abc など）
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "リクエストのパラメーターが正しくありません"));
    }

    // 自分自身をフォローしようとした、など
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        // 他人の投稿を編集・削除しようとした、など。画面からは起きないので、続くなら不正な操作の試み
        log.atWarn()
                .addKeyValue(LogContext.EVENT, LogEvents.ACCESS_DENIED)
                .addKeyValue("reason", "forbidden")
                .log("権限のない操作を拒否しました: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", ex.getMessage()));
    }

    // 画像が大きすぎる（投稿の画像は5MB、プロフィール画像は2MB。メッセージは投げた側で決める）
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("PAYLOAD_TOO_LARGE", ex.getMessage()));
    }

    // Spring がリクエストを読む段階で、上限（application.properties の spring.servlet.multipart.*）を超えたとき
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("PAYLOAD_TOO_LARGE", "画像は1枚5MBまでです"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "リクエストの形式が正しくありません"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of("METHOD_NOT_ALLOWED", "この操作はできません"));
    }

    // 画像の保存先（S3）とのやり取りに失敗した
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex) {
        log.atError()
                .addKeyValue(LogContext.EVENT, LogEvents.STORAGE_FAILED)
                .setCause(ex)
                .log("画像の保存先（S3）とのやり取りに失敗しました");
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "投稿に失敗しました。時間をおいてもう一度お試しください"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", VALIDATION_MESSAGE, ex.getErrors()));
    }

    // ログイン失敗。メールアドレスとパスワードのどちらが違うかは伝えない
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", LOGIN_FAILED_MESSAGE));
    }

    // ログインの失敗が続いた（LoginAttemptService）。何秒後に試せるかを Retry-After ヘッダーで伝える
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(ErrorResponse.of("TOO_MANY_REQUESTS", ex.getMessage()));
    }

    // トークンの更新に失敗した。画面はログイン画面へ移動する
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", "ログインの有効期限が切れました。もう一度ログインしてください"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "この操作を行う権限がありません"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "見つかりません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ErrorResponse.of(status.name(), message));
    }

    // 想定外のエラー。内部の情報はレスポンスに出さず、ログにだけ残す
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.atError()
                .addKeyValue(LogContext.EVENT, LogEvents.UNEXPECTED_ERROR)
                .setCause(ex)
                .log("想定外のエラーが起きました");
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "予期しないエラーが発生しました"));
    }
}
