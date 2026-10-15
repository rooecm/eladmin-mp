package fun.rooe.admin.modules.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Persistence model for sys_dict_detail.
 */
@TableName("sys_dict_detail")
public class DictDetail implements Serializable {
    /**
     * Primary key.
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Dictionary ID.
     */
    @TableField("dict_id")
    private Long dictId;

    /**
     * Display label.
     */
    private String label;

    /**
     * Stored dictionary value.
     */
    private String value;

    /**
     * Display order.
     */
    private Integer dictSort = 999;

    /**
     * Creator username.
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * Last updater username.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * Creation time.
     */
    @TableField(fill = FieldFill.INSERT)
    private Timestamp createTime;

    /**
     * Last update time.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getDictSort() {
        return dictSort;
    }

    public void setDictSort(Integer dictSort) {
        this.dictSort = dictSort;
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
