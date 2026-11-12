package com.taglib.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taglib.user.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子累加失败次数；本次失败使累计次数达到 5 时，同一条语句内置为 TEMP_LOCKED 并锁定 15 分钟。
     * MySQL 单表 UPDATE 的 SET 从左到右求值，CASE 写在自增之前，判断的是本次失败前的次数（>=4 即本次为第 5 次）。
     * 计数用 LEAST 封顶 5；WHERE status = 'ACTIVE' 防止并发下覆盖管理员的禁用操作。
     */
    @Update("UPDATE user SET " +
            "status = CASE WHEN failed_login_count >= 4 THEN 'TEMP_LOCKED' ELSE status END, " +
            "locked_until = CASE WHEN failed_login_count >= 4 THEN DATE_ADD(NOW(), INTERVAL 15 MINUTE) ELSE locked_until END, " +
            "failed_login_count = LEAST(failed_login_count + 1, 5), " +
            "updated_at = NOW() " +
            "WHERE user_id = #{userId} AND status = 'ACTIVE'")
    int recordLoginFailure(@Param("userId") Long userId);

    /**
     * 临时锁定到期后自动解除，失败计数归零。
     * 带 locked_until 条件，避免并发请求把刚延长的锁定提前清除。
     */
    @Update("UPDATE user SET status = 'ACTIVE', failed_login_count = 0, locked_until = NULL, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND status = 'TEMP_LOCKED' " +
            "AND (locked_until IS NULL OR locked_until <= NOW())")
    int releaseExpiredLock(@Param("userId") Long userId);

    /**
     * 登录成功重置计数与锁定状态。带 status <> 'DISABLED' 条件，
     * 避免并发下覆盖管理员刚设置的禁用状态；影响行数为 0 表示账号已被禁用。
     */
    @Update("UPDATE user SET failed_login_count = 0, status = 'ACTIVE', locked_until = NULL, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND status <> 'DISABLED'")
    int resetLoginSuccess(@Param("userId") Long userId);
}
