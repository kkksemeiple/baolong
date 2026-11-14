package com.baolong.wsw.config;

import com.baolong.wsw.entity.Post;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.enums.AuthStatus;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.enums.TaskType;
import com.baolong.wsw.enums.UserRole;
import com.baolong.wsw.enums.UserStatus;
import com.baolong.wsw.mapper.PostMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baolong.wsw.service.CreditService;
import com.baolong.wsw.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 演示数据播种：只在库里一个用户都没有时执行。
 *
 * <p>为什么不把账号写进 SQL 脚本？因为 BCrypt 哈希不能手工硬编码到 SQL 里再保证和
 * 后端的编码器一致；用 Java 生成，密码规则永远和 {@code AuthService} 同步。
 * 整个方法包在 try/catch 里，播种失败也绝不影响服务启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "abc12345";

    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final PostMapper postMapper;
    private final WalletService walletService;
    private final CreditService creditService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long exists = userMapper.selectCount(null);
            if (exists != null && exists > 0) {
                return;
            }
            seed();
            log.info("演示数据播种完成，默认密码：{}", DEFAULT_PASSWORD);
        } catch (Exception e) {
            log.warn("演示数据播种失败（不影响启动）：{}", e.getMessage());
        }
    }

    private void seed() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        User admin = createUser(encoder, "admin", "暴龙管理员", "13800000000",
                UserRole.SADMIN, AuthStatus.APPROVED);
        User lin = createUser(encoder, "linxiaoman", "林小满", "13800000001",
                UserRole.USER, AuthStatus.APPROVED);
        User zhao = createUser(encoder, "zhaolei", "赵磊", "13800000002",
                UserRole.USER, AuthStatus.NONE);
        User chen = createUser(encoder, "chenyu", "陈宇", "13800000003",
                UserRole.USER, AuthStatus.APPROVED);

        // 给演示用户多发点钱，方便直接发单
        walletService.grant(lin.getId(), 1900L, com.baolong.wsw.enums.BizType.ADMIN,
                admin.getId(), "演示数据充值", "SEED", lin.getId());
        walletService.grant(zhao.getId(), 500L, com.baolong.wsw.enums.BizType.ADMIN,
                admin.getId(), "演示数据充值", "SEED", zhao.getId());

        createTask(lin, "帮忙取一下菜鸟驿站的快递", TaskType.PICKUP, 8L, 3,
                "南校区菜鸟驿站", 1, "代取,快递", 6);
        createTask(lin, "求带一份二食堂的黄焖鸡", TaskType.BUY, 12L, 2,
                "二食堂", 1, "代买,带饭", 3);
        createTask(zhao, "高数期末一对一辅导两小时", TaskType.TUTOR, 120L, 48,
                "图书馆 3 楼自习区", 1, "家教,高数", 24);
        createTask(zhao, "帮忙搬两个行李箱到 6 号楼", TaskType.MOVE, 30L, 5,
                "1 号楼楼下", 1, "搬运,宿舍", 10);
        createTask(chen, "借用一下微单相机一天", TaskType.RENT, 60L, 24,
                "东区宿舍 5 栋", 1, "租赁,相机", 12);
        createTask(chen, "代刷一次校园跑 2 公里", TaskType.PICKUP, 15L, 8,
                "操场", 1, "代课,跑步", 4);

        createPost(chen, "期末周自习室占座心得", "分享几个安静的自习室楼层和最佳去的时间段……", "学习,自习");
        createPost(lin, "推荐二食堂新出的麻辣香锅", "分量足，两个人一起吃刚好，人均 15 左右。", "美食,推荐");
        createPost(zhao, "求组队参加程序设计大赛", "目前两个人，还差一个熟悉前端的同学。", "竞赛,组队");

        creditService.refresh(admin.getId());
        creditService.refresh(lin.getId());
        creditService.refresh(zhao.getId());
        creditService.refresh(chen.getId());
    }

    private User createUser(BCryptPasswordEncoder encoder, String username, String nickname,
                            String phone, UserRole role, AuthStatus authStatus) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(DEFAULT_PASSWORD));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setRole(role.name());
        user.setStatus(UserStatus.NORMAL.name());
        user.setAuthStatus(authStatus.name());
        user.setRecommendEnabled(Boolean.TRUE);
        user.setIntro("这是演示账号：" + username);
        user.setTags("学习,生活");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        walletService.getOrCreate(user.getId());
        return user;
    }

    private void createTask(User publisher, String title, TaskType type, long reward,
                            int deadlineHours, String location, int peopleNeeded,
                            String tags, int startInHours) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(title + "，有意向的同学请接单后联系我。");
        task.setType(type.name());
        task.setReward(reward);
        task.setDeadline(LocalDateTime.now().plusHours(deadlineHours));
        task.setStartTime(LocalDateTime.now().plusHours(startInHours));
        task.setDurationMinutes(60);
        task.setLocation(location);
        task.setPeopleNeeded(peopleNeeded);
        task.setTags(tags);
        task.setStatus(TaskStatus.WAITING.name());
        task.setPublisherId(publisher.getId());
        task.setViewCount(0);
        task.setFavorCount(0);
        task.setVersion(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        walletService.freeze(publisher.getId(), reward, com.baolong.wsw.enums.BizType.TASK,
                task.getId(), "发布任务冻结：" + title, "TASK", task.getId());
    }

    private void createPost(User author, String title, String content, String tags) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setAuthorId(author.getId());
        post.setTags(tags);
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus("NORMAL");
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);
    }
}
