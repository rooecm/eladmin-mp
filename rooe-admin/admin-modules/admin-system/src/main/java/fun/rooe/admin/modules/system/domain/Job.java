package fun.rooe.admin.modules.system.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Persistence model for sys_job.
 */
@TableName("sys_job")
public class Job implements Serializable {
  /**
   * Primary key.
   */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /**
   * Job name.
   */
  private String name;

  /**
   * Whether the job is enabled.
   */
  private Boolean enabled;

  /**
   * Display order.
   */
  private Long jobSort;

    /** Creator username. */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** Last updater username. */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** Creation time. */
    @TableField(fill = FieldFill.INSERT)
    private Timestamp createTime;

    /** Last update time. */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Long getJobSort() {
    return jobSort;
  }

  public void setJobSort(Long jobSort) {
    this.jobSort = jobSort;
  }

  public String getCreateBy() {
    return createBy;
  }

  public void setCreateBy(String createBy) {
    this.createBy = createBy;
  }

  public String getUpdateBy() {
    return updateBy;
  }

  public void setUpdateBy(String updateBy) {
    this.updateBy = updateBy;
  }

  public Timestamp getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Timestamp createTime) {
    this.createTime = createTime;
  }

  public Timestamp getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }
}
