package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.ResultCode;
import com.baolong.wsw.config.WswProperties;
import com.baolong.wsw.dto.AuthDto;
import com.baolong.wsw.entity.LoginLog;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.entity.WalletAccount;
import com.baolong.wsw.enums.AuthStatus;
import com.baolong.wsw.enums.UserRole;
import com.baolong.wsw.enums.UserStatus;
import com.baolong.wsw.mapper.LoginLogMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baolong.wsw.security.JwtUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 账号服务：注册、登录、改密、实名认证、登录记录。
 * <p>注册时会同步做三件事：建钱包（送 100 屋币）、建信用档案（先验分 100）、发欢迎通知。
 * 三者任一失败整个注册回滚，不会出现「有账号没钱包」的脏数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 失败次数滑动窗口（进程内）。登录与找回密码都靠它限速：
     * 不加限制的话，密码可以被无限次猜；更糟的是「登录名 + 手机号」就能改密码，
     * 手机号是 11 位数字、可枚举，等于给了一条离线爆破账号的捷径。
     */
    private static final int MAX_FAILS = 5;
    private static final long FAIL_WINDOW_MS = 10 * 60 * 1000L;
    /** 静态：非静态 final 字段会被 Lombok 的 @RequiredArgsConstructor 当成依赖注入 */
    private static final Map<String, Deque<Long>> FAILURES = new ConcurrentHashMap<>();

    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final LoginLogMapper loginLogMapper;
    private final WalletService walletService;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final JwtUtil jwtUtil;
    private final WswProperties properties;

    // ------------------------------------------------------------------ 注册登录

    @Transactional(rollbackFor = Exception.class)
    public AuthDto.LoginVO register(AuthDto.RegisterReq req) {
        String username = req.username().trim();
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw BizException.conflict("登录名已被占用");
        }
        if (req.phone() != null) {
            Long phoneExists = userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, req.phone()));
            if (phoneExists != null && phoneExists > 0) {
                throw BizException.conflict("手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(ENCODER.encode(req.password()));
        user.setNickname(req.nickname().trim());
        user.setPhone(req.phone());
        user.setStudentNo(req.studentNo());
        user.setRole(UserRole.USER.name());
        user.setStatus(UserStatus.NORMAL.name());
        user.setAuthStatus(AuthStatus.NONE.name());
        user.setRecommendEnabled(Boolean.TRUE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 两个请求同时通过上面的查重（查重和插入之间有窗口），
            // 真正拦住重复的是数据库唯一索引；这里把它翻译成人话，别让用户看到 500。
            throw BizException.conflict("登录名或手机号已被占用");
        }

        // 钱包 + 信用档案
        WalletAccount wallet = walletService.getOrCreate(user.getId());
        creditService.refresh(user.getId());

        // 注册即登录，也要留一条登录记录；否则「最近登录记录」在刚注册的账号上是空的，
        // 看起来像功能坏了。
        String[] info = clientInfo();
        writeLoginLog(user.getId(), true, info[0], info[1]);

        // 欢迎语里的金额必须读配置，不能写死 100——
        // 后台把「新用户注册赠送」改成 120 之后，钱包已经发 120，文案却还写 100。
        long grant = configService.getLong("initGrant", 100);
        if (grant > 0) {
            notificationService.pushSystem(user.getId(), "欢迎来到暴龙万事屋",
                    "注册赠送 " + grant + " 屋币已到账，去任务大厅看看吧");
        } else {
            notificationService.pushSystem(user.getId(), "欢迎来到暴龙万事屋",
                    "账号已开通，去任务大厅看看吧");
        }
        log.debug("新用户注册完成: userId={}, walletAvailable={}",
                user.getId(), wallet == null ? null : wallet.getAvailable());

        return issueToken(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthDto.LoginVO login(AuthDto.LoginReq req, String ip, String userAgent) {
        String account = req.username() == null ? "" : req.username().trim();
        ensureNotThrottled(account);
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, account));
        if (user == null || !ENCODER.matches(req.password(), user.getPasswordHash())) {
            recordFailure(account);
            writeLoginLog(user == null ? null : user.getId(), false, ip, userAgent);
            throw BizException.of(ResultCode.UNAUTHORIZED, "账号或密码不正确");
        }
        if (UserStatus.BANNED.name().equals(user.getStatus())) {
            writeLoginLog(user.getId(), false, ip, userAgent);
            throw BizException.forbidden("账号已被封禁，请联系管理员");
        }
        clearFailures(account);

        user.setLastActiveAt(LocalDateTime.now());
        userMapper.updateById(user);
        writeLoginLog(user.getId(), true, ip, userAgent);
        return issueToken(user);
    }

    public AuthDto.UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        return toVO(user);
    }

    // ------------------------------------------------------------------ 密码

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, AuthDto.ChangePasswordReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        if (!ENCODER.matches(req.oldPassword(), user.getPasswordHash())) {
            throw BizException.badRequest("原密码不正确");
        }
        user.setPasswordHash(ENCODER.encode(req.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 忘记密码第一步：核对登录名与手机号。
     * <p>课程项目没有接短信服务，这里直接把手机号是否匹配作为校验依据，
     * 返回脱敏手机号让前端展示，真正的重置在 {@link #resetPassword} 里完成。
     */
    public String forgotPassword(AuthDto.ForgotReq req) {
        String account = req.username() == null ? "" : req.username().trim();
        ensureNotThrottled(account);
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, account));
        if (user == null) {
            recordFailure(account);
            throw BizException.notFound("账号不存在");
        }
        if (user.getPhone() == null || !user.getPhone().equals(req.phone())) {
            recordFailure(account);
            throw BizException.badRequest("手机号与账号不匹配");
        }
        clearFailures(account);
        return maskPhone(user.getPhone());
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AuthDto.ResetReq req) {
        String account = req.username() == null ? "" : req.username().trim();
        ensureNotThrottled(account);
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, account));
        if (user == null) {
            recordFailure(account);
            throw BizException.notFound("账号不存在");
        }
        if (user.getPhone() == null || !user.getPhone().equals(req.phone())) {
            recordFailure(account);
            throw BizException.badRequest("手机号与账号不匹配");
        }
        clearFailures(account);
        user.setPasswordHash(ENCODER.encode(req.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------ 限速

    private void ensureNotThrottled(String account) {
        Deque<Long> records = FAILURES.get(account);
        if (records == null) {
            return;
        }
        long since = System.currentTimeMillis() - FAIL_WINDOW_MS;
        synchronized (records) {
            while (!records.isEmpty() && records.peekFirst() < since) {
                records.pollFirst();
            }
            if (records.size() >= MAX_FAILS) {
                throw BizException.badRequest("尝试次数过多，请 10 分钟后再试");
            }
        }
    }

    private void recordFailure(String account) {
        Deque<Long> records = FAILURES.computeIfAbsent(account, k -> new ArrayDeque<>());
        synchronized (records) {
            records.addLast(System.currentTimeMillis());
        }
    }

    private void clearFailures(String account) {
        FAILURES.remove(account);
    }

    // ------------------------------------------------------------------ 实名认证

    public AuthDto.CertStatusVO studentCertStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        return new AuthDto.CertStatusVO(user.getAuthStatus(), user.getStudentNo(),
                user.getRealName(), user.getCertImage(), user.getCertRejectReason());
    }

    @Transactional(rollbackFor = Exception.class)
    public void applyStudentCert(Long userId, AuthDto.StudentCertReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED);
        }
        String studentNo = req.studentNo().trim();
        String realName = req.realName().trim();

        // 学号唯一：同一个学号不允许同时挂在两个账号上。
        // 这是「A 账号改成 B 的实名」最直接的防线——否则一个学号能认证无数个号。
        // 只和「审核中 / 已认证」的账号比：被驳回的旧记录不该长期占位。
        Long taken = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getStudentNo, studentNo)
                .ne(User::getId, userId)
                .in(User::getAuthStatus, AuthStatus.PENDING.name(), AuthStatus.APPROVED.name()));
        if (taken != null && taken > 0) {
            throw BizException.conflict("该学号已被其他账号用于实名认证");
        }

        boolean wasApproved = AuthStatus.APPROVED.name().equals(user.getAuthStatus());
        if (AuthStatus.PENDING.name().equals(user.getAuthStatus())) {
            throw BizException.conflict("认证申请审核中，请耐心等待；如需修改请等审核结果");
        }
        // 已认证的人重新提交 = 申请变更，允许，但要重新走审核，并且必须真的改了东西
        if (wasApproved
                && studentNo.equals(user.getStudentNo())
                && realName.equals(user.getRealName())
                && (req.certImage() == null || req.certImage().isBlank()
                    || req.certImage().trim().equals(user.getCertImage()))) {
            throw BizException.conflict("实名信息没有变化，无需重新提交");
        }
        if (wasApproved) {
            log.info("实名信息变更申请: userId={}, {}({}) -> {}({})",
                    userId, user.getRealName(), user.getStudentNo(), realName, studentNo);
        }

        user.setStudentNo(studentNo);
        user.setRealName(realName);
        if (req.certImage() != null && !req.certImage().isBlank()) {
            user.setCertImage(req.certImage().trim());
        }
        // 变更后回到待审核：管理员要重新核对「新照片」与「新姓名/学号」是否一致
        user.setAuthStatus(AuthStatus.PENDING.name());
        user.setCertRejectReason(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------ 登录记录

    public List<AuthDto.LoginLogVO> loginLogs(Long userId) {
        List<LoginLog> logs = loginLogMapper.selectList(
                Wrappers.<LoginLog>lambdaQuery()
                        .eq(LoginLog::getUserId, userId)
                        .orderByDesc(LoginLog::getId)
                        .last("LIMIT 20"));
        List<AuthDto.LoginLogVO> result = new ArrayList<>(logs.size());
        for (LoginLog log : logs) {
            result.add(new AuthDto.LoginLogVO(
                    log.getId(),
                    log.getIp(),
                    describeIp(log.getIp()),
                    log.getDevice(),
                    log.getSuccess(),
                    log.getSuccess() != null && log.getSuccess() ? null : log.getMsg(),
                    log.getCreatedAt()));
        }
        return result;
    }

    /**
     * 把 IP 粗分成「本机 / 局域网 / 外网」。
     * <p>课程项目不接 IP 归属地库，本地开发时看到的 ::1 就是本机回环，
     * 直接显示成「本机」，比让用户对着一串冒号猜要好。
     */
    private static String describeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "未知";
        }
        String value = ip.trim();
        if ("127.0.0.1".equals(value) || "::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)
                || "localhost".equalsIgnoreCase(value)) {
            return "本机";
        }
        if (value.startsWith("192.168.") || value.startsWith("10.") || value.startsWith("172.16.")
                || value.startsWith("172.17.") || value.startsWith("172.18.") || value.startsWith("172.19.")
                || value.startsWith("172.2") || value.startsWith("172.30.") || value.startsWith("172.31.")) {
            return "局域网";
        }
        return "外网";
    }

    // ------------------------------------------------------------------ 内部

    private AuthDto.LoginVO issueToken(User user) {
        // 把密码指纹写进 token：用户一改密码，旧 token 的指纹就对不上，
        // 拦截器会判 401 —— 这才真的兑现「改密码后所有设备下线」这句提示。
        String token = jwtUtil.create(user.getId(), user.getUsername(), user.getRole(),
                com.baolong.wsw.security.JwtUtil.fingerprint(user.getPasswordHash()));
        return new AuthDto.LoginVO(token, properties.getJwt().getExpireSeconds(), toVO(user));
    }

    public AuthDto.UserVO toVO(User user) {
        UserCredit credit = userCreditMapper.selectById(user.getId());
        int score = credit == null || credit.getCreditScore() == null
                ? 100 : (int) Math.round(credit.getCreditScore().doubleValue());
        int goodRate = credit == null || credit.getReviewScore() == null
                ? 100 : (int) Math.round(credit.getReviewScore().doubleValue() / 5 * 100);
        int finished = credit == null || credit.getFinishedClaims() == null
                ? 0 : credit.getFinishedClaims();
        return new AuthDto.UserVO(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getNickname(),
                user.getAvatar(),
                // 本人视角返回完整手机号（个人中心要能看到注册时用的号码）；
                // 他人主页由 UserCenterService.profile 负责打码。
                user.getPhone(),
                score,
                user.getStatus(),
                user.getAuthStatus(),
                finished,
                goodRate,
                splitTags(user.getTags()),
                user.getIntro(),
                user.getRecommendEnabled(),
                user.getCreatedAt());
    }

    private void writeLoginLog(Long userId, boolean success, String ip, String userAgent) {
        try {
            LoginLog log = new LoginLog();
            log.setUserId(userId);
            log.setIp(ip);
            log.setDevice(truncate(userAgent, 200));
            log.setSuccess(success);
            log.setCreatedAt(LocalDateTime.now());
            loginLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("登录记录写入失败", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** 从当前请求里取 ip 与 UA（登录记录用） */
    public static String[] clientInfo() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return new String[]{null, null};
            }
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            if (ip == null) {
                ip = request.getRemoteAddr();
            }
            return new String[]{ip, request.getHeader("User-Agent")};
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }
}
