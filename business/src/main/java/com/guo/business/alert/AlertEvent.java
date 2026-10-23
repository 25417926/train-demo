package com.guo.business.alert;

import java.util.Date;

/**
 * I18 告警记录：规则、对象、观测值与状态。
 *
 * <p>触发时写入历史并输出 ERROR 日志；指标恢复后状态置为 RESOLVED。</p>
 */
public class AlertEvent {

    /** FIRING / RESOLVED */
    public static final String STATUS_FIRING = "FIRING";
    public static final String STATUS_RESOLVED = "RESOLVED";

    private String id;
    private String rule;
    private String level;
    private String target;
    private String message;
    private double value;
    private Date triggeredAt;
    private String status;
    private Date recoveredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Date getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Date triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getRecoveredAt() {
        return recoveredAt;
    }

    public void setRecoveredAt(Date recoveredAt) {
        this.recoveredAt = recoveredAt;
    }
}
