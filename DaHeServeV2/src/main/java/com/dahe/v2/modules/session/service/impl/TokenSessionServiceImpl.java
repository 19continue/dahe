package com.dahe.v2.modules.session.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dahe.v2.modules.session.mapper.TokenSessionMapper;
import com.dahe.v2.modules.session.model.SessionDeviceContext;
import com.dahe.v2.modules.session.model.TokenSession;
import com.dahe.v2.modules.session.service.TokenSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
/**
 * token 会话服务实现。
 *
 * <p>实现策略：数据库为最终事实来源；当开启 Redis 时，Redis 仅做会话读取加速缓存。</p>
 */
public class TokenSessionServiceImpl extends ServiceImpl<TokenSessionMapper, TokenSession> implements TokenSessionService {

    private static final Logger log = LoggerFactory.getLogger(TokenSessionServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;
    private final String redisKeyPrefix;
    private final long redisCacheMaxTtlSeconds;
    private final long redisFailureBypassMillis;
    private volatile long redisBypassUntilAt = 0L;

    public TokenSessionServiceImpl(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${app.session.redis-enabled:false}") boolean redisEnabled,
            @Value("${app.session.redis-key-prefix:dahe:v2:session:}") String redisKeyPrefix,
            @Value("${app.session.redis-cache-max-ttl-minutes:360}") long redisCacheMaxTtlMinutes,
            @Value("${app.session.redis-failure-bypass-seconds:60}") long redisFailureBypassSeconds
    ) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
        this.redisKeyPrefix = StringUtils.hasText(redisKeyPrefix) ? redisKeyPrefix.trim() : "dahe:v2:session:";
        this.redisCacheMaxTtlSeconds = Math.max(60L, redisCacheMaxTtlMinutes) * 60L;
        this.redisFailureBypassMillis = Math.max(1L, redisFailureBypassSeconds) * 1000L;
    }

    @Override
    // 查询有效会话：优先读 Redis 缓存，未命中回源数据库。
    public TokenSession findValidByToken(String token) {
        /*
         * session 查询链路：
         * 1. 先查 Redis 热点缓存；
         * 2. Redis 未命中时回源数据库；
         * 3. 数据库查到有效 session 后再回写 Redis。
         *
         * 这里一定要记住：数据库才是最终真相，Redis 只是读加速层。
         * 这样设计的好处是，即使 Redis 暂时不可用，系统也还能回退到数据库继续工作。
         */
        if (!StringUtils.hasText(token)) {
            return null;
        }
        // 统一 trim，避免请求头里多余空格造成缓存键和数据库查询不一致。
        String normalizedToken = token.trim();
        // 第一步先查缓存，命中时直接返回。
        TokenSession cached = readSessionCache(normalizedToken);
        if (cached != null) {
            return cached;
        }
        // 缓存没命中，再回源数据库查最终真相。
        TokenSession row = findValidByTokenFromDb(normalizedToken);
        // 回源成功后再回填 Redis，给后续请求提速。
        cacheSession(row);
        return row;
    }

    @Override
    // 创建新会话。有效期最短 1 天，避免调用方传入 0 或负数导致立即过期。
    public TokenSession createSession(Long userId, String userType, String loginScene, SessionDeviceContext deviceContext, int validDays) {
        /*
         * 创建 session 前，会先把同用户、同用户类型、同登录场景下的旧 session 失效掉。
         * 当前实现更偏“单用户单场景保留最新会话”，方便减少多 token 并存带来的状态复杂度。
         */
        String normalizedUserType = normalizeUserType(userType);
        String normalizedLoginScene = normalizeLoginScene(loginScene);
        // 当前实现希望同一用户在同一场景下只保留一个最新 token。
        invalidateByUserId(userId, normalizedUserType, normalizedLoginScene);

        TokenSession row = new TokenSession();
        // 绑定用户 id。
        row.setUserId(userId);
        // 记录用户类型，便于后续区分 admin / miniapp。
        row.setUserType(normalizedUserType);
        // 记录登录场景。
        row.setLoginScene(normalizedLoginScene);
        // 设备信息先做归一化，避免异常长字符串直接落库。
        SessionDeviceContext normalizedContext = normalizeDeviceContext(deviceContext);
        row.setDeviceId(normalizedContext.getDeviceId());
        row.setDeviceName(normalizedContext.getDeviceName());
        row.setClientIp(normalizedContext.getClientIp());
        row.setUserAgent(normalizedContext.getUserAgent());
        // token 用随机 UUID，避免可预测。
        row.setAccessToken(IdUtil.fastSimpleUUID());
        // 1 表示有效会话。
        row.setStatus(1);
        // 最短至少保留 1 天，防止传 0 或负数直接过期。
        row.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, validDays)));
        // 先落数据库。
        this.save(row);
        // 再写缓存。
        cacheSession(row);
        return row;
    }

    @Override
    // 失效会话采用软失效（status=0），保留历史记录便于审计与排障。
    public void invalidateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String normalizedToken = token.trim();
        deleteSessionCache(normalizedToken);

        LambdaQueryWrapper<TokenSession> qw = new LambdaQueryWrapper<>();
        qw.eq(TokenSession::getAccessToken, normalizedToken)
                .eq(TokenSession::getStatus, 1);
        TokenSession row = this.getOne(qw, false);
        if (row != null) {
            row.setStatus(0);
            this.updateById(row);
        }
    }

    @Override
    public long invalidateByUserId(Long userId) {
        return invalidateByUserId(userId, null, null);
    }

    @Override
    public long invalidateByUserId(Long userId, String userType, String loginScene) {
        if (userId == null || userId <= 0) {
            return 0L;
        }
        String normalizedUserType = normalizeUserType(userType);
        String normalizedScene = normalizeLoginScene(loginScene);

        LambdaQueryWrapper<TokenSession> qw = new LambdaQueryWrapper<>();
        qw.eq(TokenSession::getUserId, userId)
                .eq(TokenSession::getStatus, 1);
        if (StringUtils.hasText(normalizedUserType)) {
            qw.eq(TokenSession::getUserType, normalizedUserType);
        }
        if (StringUtils.hasText(normalizedScene)) {
            qw.eq(TokenSession::getLoginScene, normalizedScene);
        }
        List<TokenSession> rows = this.list(qw);
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        for (TokenSession row : rows) {
            if (row == null) {
                continue;
            }
            row.setStatus(0);
        }
        this.updateBatchById(rows, 200);
        for (TokenSession row : rows) {
            if (row == null || !StringUtils.hasText(row.getAccessToken())) {
                continue;
            }
            deleteSessionCache(row.getAccessToken());
        }
        return rows.size();
    }

    private TokenSession findValidByTokenFromDb(String token) {
        LambdaQueryWrapper<TokenSession> qw = new LambdaQueryWrapper<>();
        qw.eq(TokenSession::getAccessToken, token)
                .eq(TokenSession::getStatus, 1)
                .ge(TokenSession::getExpiresAt, LocalDateTime.now())
                .last("limit 1");
        return this.getOne(qw, false);
    }

    private void cacheSession(TokenSession row) {
        /*
         * 把数据库里的有效 session 投影到 Redis。
         *
         * 注意这里缓存的不是整行数据库对象，而是运行期校验需要的最小字段集合：
         * userId / userType / loginScene / status / expiresAt。
         * 这样可以降低缓存体积，也减少序列化字段变化带来的兼容问题。
         */
        if (!isRedisAvailable() || row == null || !StringUtils.hasText(row.getAccessToken()) || row.getExpiresAt() == null) {
            return;
        }
        try {
            // TTL 直接按 expiresAt 计算，保证缓存不会比真实会话活得更久。
            long ttlSeconds = Duration.between(LocalDateTime.now(), row.getExpiresAt()).getSeconds();
            if (ttlSeconds <= 0) {
                return;
            }
            // 再用统一上限做一次保护，避免 TTL 异常过长。
            ttlSeconds = Math.min(ttlSeconds, redisCacheMaxTtlSeconds);
            CachedSession payload = new CachedSession();
            // 这里只放会话校验真正需要的字段，避免缓存对象过重。
            payload.setUserId(row.getUserId());
            payload.setUserType(row.getUserType());
            payload.setLoginScene(row.getLoginScene());
            payload.setStatus(row.getStatus());
            payload.setExpiresAt(row.getExpiresAt());
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(buildRedisKey(row.getAccessToken()), json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 一旦 Redis 读写失败，不在当前请求上无限重试，而是短暂进入 bypass 窗口。
            markRedisFailure();
            log.warn("Cache token session failed, token={}, err={}", row.getAccessToken(), String.valueOf(e.getMessage()));
        }
    }

    private TokenSession readSessionCache(String token) {
        /*
         * 从 Redis 恢复 session 时，会再次做一层有效性判断：
         * - status 必须仍是 1；
         * - expiresAt 不能过期。
         *
         * 也就是说，即使缓存里有值，只要状态不对或已经过期，也会主动删掉缓存并视为未命中。
         */
        if (!isRedisAvailable()) {
            return null;
        }
        try {
            // 先按 token 拼出 Redis key，再读取缓存字符串。
            String json = stringRedisTemplate.opsForValue().get(buildRedisKey(token));
            if (!StringUtils.hasText(json)) {
                return null;
            }
            // 把缓存字符串反序列化成轻量对象。
            CachedSession payload = objectMapper.readValue(json, CachedSession.class);
            // status 不对或关键字段缺失时，说明这是无效缓存，直接删掉。
            if (payload == null || payload.getExpiresAt() == null || payload.getStatus() == null || payload.getStatus() != 1) {
                deleteSessionCache(token);
                return null;
            }
            // 命中过期缓存时，也主动清理，避免重复命中脏值。
            if (payload.getExpiresAt().isBefore(LocalDateTime.now())) {
                deleteSessionCache(token);
                return null;
            }
            // 再恢复成运行期需要的 TokenSession 对象。
            TokenSession row = new TokenSession();
            row.setAccessToken(token);
            row.setUserId(payload.getUserId());
            row.setUserType(payload.getUserType());
            row.setLoginScene(payload.getLoginScene());
            row.setStatus(payload.getStatus());
            row.setExpiresAt(payload.getExpiresAt());
            return row;
        } catch (Exception e) {
            // Redis 异常时快速降级到数据库，避免每次请求都先卡在 Redis 超时上。
            markRedisFailure();
            log.warn("Read token session cache failed, token={}, err={}", token, String.valueOf(e.getMessage()));
            return null;
        }
    }

    private void deleteSessionCache(String token) {
        /*
         * token 失效时主动删缓存，尽量减少“数据库已失效但缓存仍短暂命中”的窗口。
         * 这里仍然采用失败即 bypass 的思路，优先保证主流程可继续走下去。
         */
        if (!isRedisAvailable() || !StringUtils.hasText(token)) {
            return;
        }
        try {
            // 删除缓存失败不是致命错误，数据库仍可兜底。
            stringRedisTemplate.delete(buildRedisKey(token.trim()));
        } catch (Exception e) {
            markRedisFailure();
            log.warn("Delete token session cache failed, token={}, err={}", token, String.valueOf(e.getMessage()));
        }
    }

    private boolean isRedisAvailable() {
        /*
         * Redis 是否可用不只看配置和 bean 是否存在，还要看当前是否处于 bypass 窗口。
         * bypass 窗口内统一跳过 Redis，直接回数据库，避免故障扩散。
         */
        return redisEnabled && stringRedisTemplate != null && System.currentTimeMillis() >= redisBypassUntilAt;
    }

    private void markRedisFailure() {
        /*
         * 短暂 bypass 的核心实现。
         *
         * 一旦 Redis 出异常，就把“绕过 Redis 的截止时间”向后推一段固定时间。
         * 在这段时间里，所有 session 读取直接回数据库。
         *
         * 这不是完美容灾，而是一种很务实的工程降级策略：
         * - 优点：避免所有请求重复踩 Redis 故障；
         * - 代价：数据库压力会暂时变大。
         */
        // 记录 bypass 截止时间；在这段窗口内统一绕过 Redis。
        redisBypassUntilAt = System.currentTimeMillis() + redisFailureBypassMillis;
    }

    private String buildRedisKey(String token) {
        return redisKeyPrefix + token;
    }

    private String normalizeUserType(String userType) {
        String raw = String.valueOf(userType == null ? "" : userType).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(raw) ? raw : null;
    }

    private String normalizeLoginScene(String loginScene) {
        String raw = String.valueOf(loginScene == null ? "" : loginScene).trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(raw) ? raw : null;
    }

    private SessionDeviceContext normalizeDeviceContext(SessionDeviceContext rawContext) {
        SessionDeviceContext out = new SessionDeviceContext();
        if (rawContext == null) {
            return out;
        }
        out.setDeviceId(trimToLength(rawContext.getDeviceId(), 64));
        out.setDeviceName(trimToLength(rawContext.getDeviceName(), 128));
        out.setClientIp(trimToLength(rawContext.getClientIp(), 64));
        out.setUserAgent(trimToLength(rawContext.getUserAgent(), 512));
        return out;
    }

    private String trimToLength(String raw, int maxLength) {
        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength));
    }

    private static class CachedSession {
        private Long userId;
        private String userType;
        private String loginScene;
        private Integer status;
        private LocalDateTime expiresAt;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserType() {
            return userType;
        }

        public void setUserType(String userType) {
            this.userType = userType;
        }

        public String getLoginScene() {
            return loginScene;
        }

        public void setLoginScene(String loginScene) {
            this.loginScene = loginScene;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
