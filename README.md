# yulinlin-data

### 依赖导入

**
<dependency>
<groupId>com.yulinlin</groupId>
<artifactId>mysql</artifactId>
<version>2.0</version>
</dependency>
**


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


    @Test
    public void insert(){


        //创建用户对象
        SysUserEntity e =  new SysUserEntity();
        e.setUsername("admin");
        e.setPassword("admin");

        //获得插入构造器
        ModelInsertWrapper wrappper =ModelInsertWrapper.newInstance(e);

        //查看sql
        String sql = wrappper
                //获取sql
                .getSql();

        //insert into  sys_user ( username , email , password , id )  values  ( #{0} , #{1} , #{2} , #{3} ) 
 
        //数据库执行
        //int total = wrappper.execute();


    }


    @Test
    public void delete(){

        SysUserEntity e = new SysUserEntity();
        e.setId("1");
        //解析实体类 主键字段会作为删除条件，
        ModelDeleteWrapper wrapper =  ModelDeleteWrapper.newInstance(e);
        //查看sql
        String sql =  wrapper.getSql();
        //delete from  sys_user where id = #{0}
        //数据库执行
      	//int total =  wrapper.execute();

        //和上面等价，编程写法
         ModelDeleteWrapper.newInstance(SysUserEntity.class)
                //删除id = 1
                .eq("id",1)
                //获取sql
                .getSql();
     }

@Test
public void update(){

    SysUserEntity e = new SysUserEntity();
    e.setId("1");
    e.setEmail("123@qq.com");
    e.setUsername("admin");
    e.setPassword("admin");
    //解析实体类 主键字段会作为更新条件，其他作为值字段
    ModelUpdateWrapper wrapper =  ModelUpdateWrapper.newInstance(e);
    //查看sql
    String sql =  wrapper.getSql();
    //update  sys_user set username = #{0} , email = #{1} , password = #{2} where id = #		{3}


}

**

