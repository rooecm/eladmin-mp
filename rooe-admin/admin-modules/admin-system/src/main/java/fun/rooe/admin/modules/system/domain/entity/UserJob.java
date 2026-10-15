package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_users_jobs")
public class UserJob {
    private Long userId;
    private Long jobId;

    public UserJob() {
    }

    public UserJob(Long userId, Long jobId) {
        this.userId = userId;
        this.jobId = jobId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
}
