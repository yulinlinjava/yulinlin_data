# yulinlin-data

### 依赖导入

***
<dependency>
<groupId>com.yulinlin</groupId>
<artifactId>mysql</artifactId>
<version>2.0</version>
</dependency>
***


#### 定义数据模型

**

这个是orm框架提供的内置复用对象，提供必备id字段的类

public class IdEntity<E extends IdEntity<E>>   implements Serializable , AbstractModel<E>{

    @JoinWhere
    @JoinMeta(primaryKey = true)
    @JoinField
    @ApiModelProperty("id")
    private String id;



    @Override
    public void insertBefore() {
        if(this.id == null || id.isEmpty()){
            this.id =generateId();
        }
    }

    public String nextIncrId(){
        AtomicLong longAdder = MaxNumberUtil.of(this.getClass());
        String v = longAdder.incrementAndGet()+"";
        return v;
    }

    public String generateId(){
        return  SnowflakeUtil.nextIdStr();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}


这个是orm框架提供的内置复用对象，提供必备时间字段的类
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


使用
import com.yulinlin.common.domain.SuperEntity;
import com.yulinlin.data.core.anno.JoinField;
import com.yulinlin.data.core.anno.JoinTable;
import com.yulinlin.data.core.anno.JoinWhere;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@ApiModel("系统用户")
@JoinTable("sys_user")
public class SysUserEntity   extends SuperEntity<SysUserEntity>  {




        @NotEmpty(message = "必填")
        @ApiModelProperty("账号")
        @JoinWhere
        @JoinField
        private String username;

        @NotEmpty(message = "必填")
        @ApiModelProperty("密码")
        @JoinWhere
        @JoinField
        private String password;



        //orm框架会自动转为json数据,取出来会自动转换类型
        @NotEmpty(message = "必填")
        @ApiModelProperty("复杂数据")
        @JoinWhere
        @JoinField
        private Map<String,Object> data;


}


package com.yulinlin.admin;


import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.common.model.ModelUpdateWrapper;
import com.yulinlin.data.lang.util.Page;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

//
@Slf4j
@SpringBootTest
public class CrudApplicationTests {



    @SneakyThrows
    @Test
    public void insert(){

        SysUserEntity sysUserEntity = new SysUserEntity();

        sysUserEntity.setNickname("随机账号");
        sysUserEntity.setPassword("随机密码");

        //返回插入结果。可能抛出异常
        int execute = ModelInsertWrapper.newInstance(sysUserEntity)
                .execute();

        int a = 0;
    }

    @SneakyThrows
    @Test
    public void select(){
        //查询列表
        List<SysUserEntity> execute = ModelSelectWrapper.newInstance(SysUserEntity.class)
                //相等 =
                .eq(SysUserEntity::getId,1)
                //属于  id in (1,2,3)
                .in(SysUserEntity::getId, Arrays.asList(1,2,3))
                //模糊 like
                .like(SysUserEntity::getNickname,"11")

                //大于 >
                .gt(SysUserEntity::getId,1)
                //大于等于 >=
                .gte(SysUserEntity::getId,1)

                //排序
                .orderByAsc(SysUserEntity::getId)
                //逆序
                .orderByDesc(SysUserEntity::getId)
                //分页参数
                .page(1,100)
                //执行查询 selectOne 是查询一个

                .selectList();

    }

    @SneakyThrows
    @Test
    public void selectPage(){
        /**
         * @Getter
         * public class Page<E>  implements Iterable<E>{
         *
         *
         *     private List<E> list;
         *
         *     private int total;
         *
         *     private Map<String,Object> extra;
         *
         *
         *     public Page() {
         *     }
         */
        Page<SysUserEntity> execute = ModelSelectWrapper.newInstance(SysUserEntity.class)
                //相等 =
                .eq(SysUserEntity::getId,1)
                //大于 >
                .gt(SysUserEntity::getId,1)
                //大于等于 >=
                .gte(SysUserEntity::getId,1)
                //模糊 like
                .like(SysUserEntity::getNickname,"11")
                //排序
                .orderByAsc(SysUserEntity::getId)
                //逆序
                .orderByDesc(SysUserEntity::getId)
                //分页查询


                .selectPage(1,100);

    }

    @SneakyThrows
    @Test
    public void update(){


        //返回修改结果。
        int execute = ModelUpdateWrapper.newInstance(SysUserEntity.class)
                //字段赋值
                .field(SysUserEntity::getNickname,1)
                //金币自增 gold = gold + 1
                .inc(SysUserEntity::getGold,1)
                //金币自减  gold = gold - 1
                .dec(SysUserEntity::getGold,1)
                .execute();

    }


    @SneakyThrows
    @Test
    public void delete(){


        //返回删除结果。   条件和查询一样用法
        int execute = ModelDeleteWrapper.newInstance(SysUserEntity.class)
                //相等 =
                .eq(SysUserEntity::getId,1)
                //属于  id in (1,2,3)
                .in(SysUserEntity::getId, Arrays.asList(1,2,3))
                //模糊 like
                .like(SysUserEntity::getNickname,"11")

                .execute();

    }

}


***

