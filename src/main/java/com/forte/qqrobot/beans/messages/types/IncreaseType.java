package com.forte.qqrobot.beans.messages.types;

/**
 * 群成员增加事件类型
 * @author ForteScarlet <[163邮箱地址]ForteScarlet@163.com>
 **/
public enum IncreaseType {


    /** 邀请 */
    INVITE(1),
    /** 通过申请 */
    AGREE(-1);

    public final int TYPE;

    IncreaseType(int type){
        this.TYPE = type;
    }

    public boolean isInvite(){
        return TYPE == INVITE.TYPE;
    }

    public boolean isAgree(){
        return TYPE == AGREE.TYPE;
    }

    /**
     * 工厂获取
     */
    public static IncreaseType of(int type) {
        for (IncreaseType value : values()) {
            if (value.TYPE == type) {
                return value;
            }
        }
        return null;
    }

}
