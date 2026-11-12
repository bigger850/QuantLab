package com.quantlab.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_task")
public class SyncTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;
    private String status;
    private Integer total;
    private Integer success;
    private Integer failed;
    private Long costMs;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
