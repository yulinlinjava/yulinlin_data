package com.yulinlin.admin;

import com.yulinlin.common.domain.TreeEntity;
import com.yulinlin.data.core.anno.*;
import com.yulinlin.data.core.event.IInitEvent;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
@ApiModel("系统菜单")
@JoinTable("sys_menu")
public class SysMenuEntity extends TreeEntity<SysMenuEntity> implements IInitEvent {


        @ApiModelProperty("标题")
        private String title;




        @JoinField(exist = false)
        @JoinQuery(primary = "parentId",value = "${id}")
        private List<SysMenuEntity> childrenList;

        @Override
        public void init() {

                System.out.println(getId());
        }
}
