package com.nuono.next.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.MobileMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbMobileAuthService {

    private static final Logger log = LoggerFactory.getLogger(LocalDbMobileAuthService.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final MobileMapper mobileMapper;
    private final LocalMobileSessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    @Value("${mobile.auth.bind-token-expire:600}")
    private int bindTokenExpireSeconds;

    @Value("${mobile.auth.sms-cooldown-seconds:60}")
    private int smsCooldownSeconds;

    @Value("${mobile.auth.debug-code-enabled:false}")
    private boolean debugCodeEnabled;

    @Value("${mobile.wechat.app-id:}")
    private String wechatAppId;

    @Value("${mobile.wechat.app-secret:}")
    private String wechatAppSecret;

    @Value("${mobile.wechat.code2session-url:https://api.weixin.qq.com/sns/jscode2session}")
    private String wechatCode2SessionUrl;

    @Value("${mobile.wechat.fallback-to-code:true}")
    private boolean wechatFallbackToCode;

    public LocalDbMobileAuthService(
            MobileMapper mobileMapper,
            LocalMobileSessionStore sessionStore,
            ObjectMapper objectMapper
    ) {
        this.mobileMapper = mobileMapper;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    public MobileAuthResponse wechatLogin(MobileWechatLoginCommand command) {
        String wechatIdentity = resolveWechatIdentity(normalize(command == null ? null : command.getCode()));
        Long userId = sessionStore.findBoundUserId(wechatIdentity);
        if (userId != null) {
            return buildAuthSuccessResponse(userId);
        }

        MobileAuthResponse response = new MobileAuthResponse();
        response.setNeedBindPhone(true);
        response.setBindToken(sessionStore.createBindToken(wechatIdentity));
        response.setExpiresIn(bindTokenExpireSeconds);
        return response;
    }

    public MobileSendCodeResponse sendCode(MobileSendCodeCommand command) {
        String phone = normalize(command == null ? null : command.getPhone());
        requireText(phone, "请输入手机号或系统账号。");

        long remainingCooldownSeconds = sessionStore.secondsUntilSmsAllowed(phone);
        if (remainingCooldownSeconds > 0) {
            throw new MobileApiException(429, "验证码发送太频繁，请 " + remainingCooldownSeconds + " 秒后再试。");
        }

        String smsCode = sessionStore.issueSmsCode(phone, smsCooldownSeconds);
        if (debugCodeEnabled) {
            log.info("nuono-next mobile debug sms code generated. phone={}, code={}", maskPhone(phone), smsCode);
        }

        MobileSendCodeResponse response = new MobileSendCodeResponse();
        response.setSuccess(true);
        response.setCooldownSeconds(smsCooldownSeconds);
        if (debugCodeEnabled) {
            response.setDebugCode(smsCode);
        }
        return response;
    }

    public MobileAuthResponse bindPhone(MobileBindPhoneCommand command) {
        String wechatIdentity = sessionStore.consumeWechatIdentity(normalize(command == null ? null : command.getBindToken()));
        if (!StringUtils.hasText(wechatIdentity)) {
            throw new MobileApiException(400, "绑定已失效，请重新微信登录。");
        }

        String phone = normalize(command == null ? null : command.getPhone());
        String captcha = normalize(command == null ? null : command.getCaptcha());
        requireText(phone, "请输入系统账号手机号或账号。");
        requireText(captcha, "请输入验证码。");
        if (!sessionStore.verifySmsCode(phone, captcha)) {
            throw new MobileApiException(400, "验证码不正确或已过期。");
        }

        MobileUserRecord user = mobileMapper.selectUserByPhoneOrAccountNo(phone);
        if (user == null) {
            throw new MobileApiException(400, "系统里还没有这个手机号或账号。");
        }

        Long boundUserId = sessionStore.findBoundUserId(wechatIdentity);
        if (boundUserId != null && !boundUserId.equals(user.getUserId())) {
            throw new MobileApiException(400, "该微信已绑定其他账号。");
        }

        sessionStore.bindWechatIdentity(wechatIdentity, user.getUserId());
        return buildAuthSuccessResponse(user.getUserId());
    }

    public MobileAuthResponse smsLogin(MobileSmsLoginCommand command) {
        String phone = normalize(command == null ? null : command.getPhone());
        String captcha = normalize(command == null ? null : command.getCaptcha());
        requireText(phone, "请输入手机号或系统账号。");
        requireText(captcha, "请输入验证码。");
        if (!sessionStore.verifySmsCode(phone, captcha)) {
            throw new MobileApiException(400, "验证码不正确或已过期。");
        }

        MobileUserRecord user = mobileMapper.selectUserByPhoneOrAccountNo(phone);
        if (user == null) {
            throw new MobileApiException(400, "系统里还没有这个手机号或账号。");
        }
        return buildAuthSuccessResponse(user.getUserId());
    }

    public MobileAuthResponse refreshToken(MobileRefreshTokenCommand command) {
        Long userId = sessionStore.consumeRefreshToken(normalize(command == null ? null : command.getRefreshToken()));
        if (userId == null) {
            throw new MobileApiException(401, "登录已过期，请重新登录。");
        }
        return buildAuthSuccessResponse(userId);
    }

    private MobileAuthResponse buildAuthSuccessResponse(Long userId) {
        MobileUserRecord user = mobileMapper.selectUserById(userId);
        if (user == null) {
            throw new MobileApiException(401, "当前账号不存在，请重新登录。");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new MobileApiException(401, "当前账号已停用，暂时不能登录。");
        }

        LocalMobileSessionStore.AuthTokens tokens = sessionStore.issueAuthTokens(userId);
        List<MobileStoreRecord> storeRecords = new ArrayList<>(mobileMapper.listStoresByUserId(userId));
        storeRecords.sort(
                Comparator.comparing((MobileStoreRecord record) -> !Boolean.TRUE.equals(record.getAuthorized()))
                        .thenComparing(record -> defaultString(record.getProjectName()))
                        .thenComparing(record -> defaultString(record.getStoreCode()))
        );

        List<MobileStoreView> stores = new ArrayList<>();
        String defaultStoreCode = null;
        for (MobileStoreRecord storeRecord : storeRecords) {
            MobileStoreView store = new MobileStoreView();
            store.setStoreCode(storeRecord.getStoreCode());
            store.setStoreName(firstNonBlank(storeRecord.getProjectName(), storeRecord.getStoreCode()));
            store.setSite(storeRecord.getSite());
            store.setIsAuthorized(Boolean.TRUE.equals(storeRecord.getAuthorized()));
            stores.add(store);
            if (defaultStoreCode == null && Boolean.TRUE.equals(storeRecord.getAuthorized())) {
                defaultStoreCode = storeRecord.getStoreCode();
            }
        }
        if (defaultStoreCode == null && !stores.isEmpty()) {
            defaultStoreCode = stores.get(0).getStoreCode();
        }

        MobileUserView userView = new MobileUserView();
        userView.setUserId(user.getUserId());
        userView.setRealName(firstNonBlank(user.getRealName(), user.getAccountNo(), user.getPhone()));
        userView.setPhone(user.getPhone());
        userView.setRoleId(user.getRoleId());
        userView.setRoleName(user.getRoleName());

        MobileAuthResponse response = new MobileAuthResponse();
        response.setNeedBindPhone(false);
        response.setToken(tokens.getAccessToken());
        response.setRefreshToken(tokens.getRefreshToken());
        response.setExpiresIn(tokens.getExpiresIn());
        response.setUser(userView);
        response.setStores(stores);
        response.setDefaultStoreCode(defaultStoreCode);
        return response;
    }

    private String resolveWechatIdentity(String code) {
        requireText(code, "微信登录 code 不能为空。");

        if (!StringUtils.hasText(wechatAppId) || !StringUtils.hasText(wechatAppSecret)) {
            return fallbackIdentity(code, "未配置微信 appId/appSecret");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildWechatCode2SessionUrl(code)))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || root.isMissingNode()) {
                return fallbackIdentity(code, "微信 code2session 返回空响应");
            }
            int errCode = root.path("errcode").asInt(0);
            if (root.has("errcode") && errCode != 0) {
                String errMsg = root.path("errmsg").asText("");
                log.warn("wechat code2session failed. errCode={}, errMsg={}, code={}", errCode, errMsg, maskCode(code));
                if (wechatFallbackToCode) {
                    return "mock_code:" + code;
                }
                throw new MobileApiException(400, "微信登录失败，请稍后重试。");
            }

            String unionId = normalize(root.path("unionid").asText(null));
            if (StringUtils.hasText(unionId)) {
                return "unionid:" + unionId;
            }
            String openId = normalize(root.path("openid").asText(null));
            if (StringUtils.hasText(openId)) {
                return "openid:" + openId;
            }
            return fallbackIdentity(code, "微信返回缺少 openid/unionid");
        } catch (MobileApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("wechat code2session request exception. code={}", maskCode(code), exception);
            return fallbackIdentity(code, "微信请求异常");
        }
    }

    private String fallbackIdentity(String code, String reason) {
        if (!wechatFallbackToCode) {
            throw new MobileApiException(400, "微信登录未完成配置，请联系管理员。");
        }
        log.info("wechat fallback to code mode. reason={}, code={}", reason, maskCode(code));
        return "mock_code:" + code;
    }

    private String buildWechatCode2SessionUrl(String code) {
        return wechatCode2SessionUrl
                + "?appid=" + urlEncode(wechatAppId)
                + "&secret=" + urlEncode(wechatAppSecret)
                + "&js_code=" + urlEncode(code)
                + "&grant_type=authorization_code";
    }

    private String urlEncode(String source) {
        return URLEncoder.encode(defaultString(source), StandardCharsets.UTF_8);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new MobileApiException(400, message);
        }
    }

    private String maskCode(String code) {
        if (!StringUtils.hasText(code) || code.length() <= 8) {
            return "***";
        }
        return code.substring(0, 4) + "***" + code.substring(code.length() - 4);
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String normalize(String value) {
        return StringUtils.trimWhitespace(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
