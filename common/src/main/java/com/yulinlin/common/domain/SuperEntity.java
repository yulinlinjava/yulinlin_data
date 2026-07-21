package com.yulinlin.common.domain;


import com.yulinlin.data.lang.util.DateTime;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;


public abstract class SuperEntity<E extends SuperEntity<E>> extends IdEntity<E>  {


    @ApiModelProperty("创建时间")
    private DateTime crtTime;

    @ApiModelProperty("修改时间")
    private DateTime uptTime;



    @Override
    public void updateBefore() {
        super.updateBefore();
        if(uptTime == null){
            this.uptTime = DateTime.now();
        }
    }



    @Override
    public void insertBefore() {
        super.insertBefore();
        if(crtTime == null){
            this.crtTime =DateTime.now();
        }
        if(uptTime == null){
            this.uptTime = DateTime.now();
        }

    }

    public DateTime getCrtTime() {
        return crtTime;
    }

    public void setCrtTime(DateTime crtTime) {
        this.crtTime = crtTime;
    }

    public DateTime getUptTime() {
        return uptTime;
    }

    public void setUptTime(DateTime uptTime) {
        this.uptTime = uptTime;
    }
}
