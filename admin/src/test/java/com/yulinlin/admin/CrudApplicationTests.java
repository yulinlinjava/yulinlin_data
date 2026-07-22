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
