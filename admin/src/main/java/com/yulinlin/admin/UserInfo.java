package com.yulinlin.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yulinlin.common.model.AbstractModel;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinMeta;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import lombok.Data;

@Data
@JoinTable("user_info")
public class UserInfo implements AbstractModel<UserInfo> {
    @JoinWhere
    @JoinField
    @JoinMeta(primaryKey = true)
    private Long id;


    @JoinWhere
    private String username;


    @JoinWhere(and = false)
    private String password;

    @JoinWhere(and = false)
    private String nickname;

    private DateTime lastActive;

    private Status status;

    private DateTime vipTime;

    private Long vipId;

    private String avatar;

    private String privilegeNumber;

    private DateTime expiredTime;

    private String upType;

    private Double hotScore;




    public static enum Status{
        freeze,
        tourist,
        user,
        check_user
    }


    public boolean isVip(){
        if(vipTime == null){
            return false;
        }
       return System.currentTimeMillis() < vipTime.getTime();
    }


}
