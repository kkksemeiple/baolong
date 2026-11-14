package com.baolong.wsw.enums;

/**
 * 任务类型。
 * <p>六类任务的差异全部由 {@link TaskTypeMeta} 描述，业务代码不写 if-else 判断类型，
 * 新增一类任务只需要在元数据里加一行。
 */
public enum TaskType {

    /** 代取（快递、外卖） */
    PICKUP("代取"),
    /** 代买（超市、食堂） */
    BUY("代买"),
    /** 租赁（借物品） */
    RENT("租赁"),
    /** 家教（课业辅导） */
    TUTOR("家教"),
    /** 搬运（行李、宿舍） */
    MOVE("搬运"),
    /** 其他 */
    OTHER("其他");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
