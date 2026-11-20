package com.power.ai.biz.order.entity;

public enum FaultType {
    PITCH("变桨系统"), GEARBOX("齿轮箱"), INVERTER("逆变器"),
    LINE("集电线路"), OTHER("其他");

    private final String desc;

    FaultType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
