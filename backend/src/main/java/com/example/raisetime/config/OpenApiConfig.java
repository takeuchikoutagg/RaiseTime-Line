package com.example.raisetime.config;

import com.example.raisetime.dto.ErrorResponse;
import com.example.raisetime.security.AuthCookies;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// API 仕様書（OpenAPI）の設定。springdoc-openapi が Controller・DTO から仕様書を作り、ここで共通の情報を足す。
// 開発（dev プロファイル）だけ公開する（application.properties の springdoc.*）。
// 書き出したものは docs/api/openapi.yaml にある（OpenApiDocumentTest で作り直す・最新か確かめる）
@Configuration
public class OpenApiConfig {

    static final String COOKIE_AUTH = "cookieAuth";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    // エラーのステータスごとの説明（docs/機能定義書/00_共通仕様.md 3章）
    static final Map<Integer, String> ERROR_DESCRIPTIONS = Map.of(
            400, "入力内容・パラメーターに誤りがある（VALIDATION_ERROR / BAD_REQUEST）",
            401, "ログインしていない、またはログインに失敗した（UNAUTHORIZED）",
            403, "CSRF トークンが無い・正しくない、または権限がない（FORBIDDEN）",
            404, "投稿・ユーザーなどが見つからない（NOT_FOUND）",
            409, "メールアドレス・@ユーザーID がすでに使われている（CONFLICT）",
            413, "画像が大きすぎる（PAYLOAD_TOO_LARGE）",
            429, "ログインの失敗が続いた（TOO_MANY_REQUESTS）。Retry-After ヘッダーの秒数のあとに試せる",
            500, "サーバーのエラー、画像の保存の失敗（INTERNAL_ERROR）");

    private static final Set<PathItem.HttpMethod> CSRF_METHODS = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

    @Bean
    public OpenAPI raisetimeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RaiseTime Line API")
                        .version("v1")
                        .description("""
                                RaiseTime Line（学習用のタイムライン型テキスト SNS）の REST API。
                                仕様の背景・業務ルールは docs/機能定義書/ を参照。

                                **ログイン**：アクセストークン（JWT）を `access_token` Cookie（HttpOnly）で受け取る。
                                Swagger UI で試すときは、POST /api/auth/login を実行すると以降の API がログインした状態で呼べる。

                                **CSRF**：POST・PUT・PATCH・DELETE は `X-XSRF-TOKEN` ヘッダーが必要。
                                先に GET /api/auth/csrf を実行して `XSRF-TOKEN` Cookie を受け取ると、Swagger UI が自動でヘッダーに入れて送る。
                                """))
                // 画面と同じオリジンで呼ぶ（開発は Vite が /api をバックエンドへ転送する）
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(AuthCookies.ACCESS_TOKEN)
                                .description("ログインすると受け取るアクセストークン（JWT、15分）"))
                        .schemas(ModelConverters.getInstance().readAll(ErrorResponse.class)));
    }

    // @ApiErrors に書いたステータスを、その API のレスポンスに足す
    @Bean
    public OperationCustomizer apiErrorsCustomizer() {
        return (operation, handlerMethod) -> {
            ApiErrors errors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (errors != null) {
                for (int status : errors.value()) {
                    addError(operation, status);
                }
            }
            return operation;
        };
    }

    // ログインが必要な API には Cookie 認証と 401 を、POST などには CSRF の 403 を足す。
    // どの API がログイン不要かは SecurityConfig と同じ一覧を使う
    @Bean
    public OpenApiCustomizer securityCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
            String[] publicPaths =
                    method == PathItem.HttpMethod.GET ? SecurityConfig.PUBLIC_GET : SecurityConfig.PUBLIC_POST;
            boolean isPublic = Arrays.asList(publicPaths).contains(path);
            if (!isPublic) {
                op.addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
                addError(op, 401);
            }
            if (CSRF_METHODS.contains(method)) {
                addError(op, 403);
            }
        }));
    }

    private static void addError(Operation operation, int status) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String description = ERROR_DESCRIPTIONS.get(status);
        if (description == null) {
            throw new IllegalArgumentException("ERROR_DESCRIPTIONS にないステータスです: " + status);
        }
        operation.getResponses().addApiResponse(String.valueOf(status), new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
    }
}
