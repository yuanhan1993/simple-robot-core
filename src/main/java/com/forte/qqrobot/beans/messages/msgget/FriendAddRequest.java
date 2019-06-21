package com.forte.qqrobot.beans.messages.msgget;

import com.forte.qqrobot.beans.messages.QQCodeAble;

/**
 * 好友添加申请事件
 * @author ForteScarlet <[163邮箱地址]ForteScarlet@163.com>
 * @since JDK1.8
 **/
public interface FriendAddRequest extends EventGet, QQCodeAble {

    /** 请求人QQ */
    String getQQ();

    @Override
    default String getQQCode(){
        return getQQ();
    }

    /** 请求消息 */
    @Override
    String getMsg();

    /** 请求的时候应该有标识一类的东西 */
    String getFlag();




}
